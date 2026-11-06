package com.example.offthegrid;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirestoreBackup {

    public interface Callback {
        void onDone(int count);

        void onError();
    }

    public interface Progress {
        void onProgress(int percent, int label);
    }

    private static final String TAG = "FirestoreBackup";
    private static final String ROOT = "backups";
    private static final String MESSAGES = "messages";
    private static final String POSTS = "posts";
    private static final String FRIENDS = "friends";
    private static final String CHATS = "chats";
    private static final String REPLIES = "replies";
    private static final String HEARTS = "hearts";
    private static final String MEDIA = "media";
    private static final String IMAGE_FIELD = "imageData";
    private static final int BATCH_SIZE = 400;
    private static final long BATCH_BYTES = 3L * 1024 * 1024;
    private static final int MAX_IMAGE_CHARS = 900000;
    private static final long AWAIT_SECONDS = 30;
    private static final long BACKUP_TIMEOUT_MS = 300000;
    private static final long RESTORE_TIMEOUT_MS = 180000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FirestoreBackup() {
    }

    private static final class Row {
        final String uid;
        final String peer;
        final String sender;
        final String text;
        final long timestamp;
        final int status;
        final String image;

        Row(String uid, String peer, String sender, String text, long timestamp, int status,
            String image) {
            this.uid = uid;
            this.peer = peer;
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
            this.status = status;
            this.image = image;
        }
    }

    private static final class Reporter {
        private final Progress progress;
        private int lastPercent = -1;
        private int lastLabel;

        Reporter(Progress progress) {
            this.progress = progress;
        }

        void report(int percent, int label) {
            int clamped = Math.max(0, Math.min(100, percent));
            if (clamped == lastPercent && label == lastLabel) return;
            lastPercent = clamped;
            lastLabel = label;
            MAIN.post(() -> progress.onProgress(clamped, label));
        }

        void range(int index, int total, int from, int to, int label) {
            if (total <= 0) {
                report(to, label);
                return;
            }
            report(from + (int) ((long) (to - from) * index / total), label);
        }
    }

    private static final class Guard {
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final Callback callback;
        private final Runnable timeout = this::fail;

        Guard(Callback callback, long timeoutMs) {
            this.callback = callback;
            MAIN.postDelayed(timeout, timeoutMs);
        }

        boolean isDone() {
            return done.get();
        }

        void succeed(int count) {
            if (done.compareAndSet(false, true)) {
                MAIN.removeCallbacks(timeout);
                MAIN.post(() -> callback.onDone(count));
            }
        }

        void fail() {
            if (done.compareAndSet(false, true)) {
                Log.w(TAG, "Operation failed or timed out");
                MAIN.removeCallbacks(timeout);
                MAIN.post(callback::onError);
            }
        }
    }

    private static final class Writer {
        private final FirebaseFirestore db;
        private WriteBatch batch;
        private int count;
        private long bytes;

        Writer(FirebaseFirestore db) {
            this.db = db;
            this.batch = db.batch();
        }

        void set(DocumentReference ref, Map<String, Object> data, long size)
                throws ExecutionException, InterruptedException, TimeoutException {
            makeRoom(size);
            batch.set(ref, data);
            count++;
            bytes += size;
        }

        void delete(DocumentReference ref)
                throws ExecutionException, InterruptedException, TimeoutException {
            makeRoom(100);
            batch.delete(ref);
            count++;
            bytes += 100;
        }

        private void makeRoom(long size)
                throws ExecutionException, InterruptedException, TimeoutException {
            if (count > 0 && (count >= BATCH_SIZE || bytes + size > BATCH_BYTES)) {
                flush();
            }
        }

        void flush() throws ExecutionException, InterruptedException, TimeoutException {
            if (count > 0) {
                await(batch.commit());
                batch = db.batch();
                count = 0;
                bytes = 0;
            }
        }
    }

    private static String accountId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public static void backup(Context context, Progress progress, Callback callback) {
        Context app = context.getApplicationContext();
        String id = accountId();
        if (id == null || !isOnline(app)) {
            callback.onError();
            return;
        }
        Guard guard = new Guard(callback, BACKUP_TIMEOUT_MS);
        Reporter reporter = new Reporter(progress);
        IO.execute(() -> {
            try {
                upload(app, id, guard, reporter);
            } catch (ExecutionException | TimeoutException e) {
                Log.w(TAG, "Backup failed", e);
                guard.fail();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                guard.fail();
            } catch (RuntimeException e) {
                Log.w(TAG, "Backup crashed", e);
                guard.fail();
            }
        });
    }

    private static void upload(Context app, String id, Guard guard, Reporter reporter)
            throws ExecutionException, InterruptedException, TimeoutException {
        reporter.report(1, R.string.progress_prepare);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference owner = db.collection(ROOT).document(id);
        Set<String> previousPosts = previousPostIds(owner);
        List<Post> myPosts = PostStore.loadMine(app);
        List<Row> rows = readRows(app);
        Writer writer = new Writer(db);
        int total = 0;

        List<String> postIds = new ArrayList<>();
        for (int i = 0; i < myPosts.size(); i++) {
            if (guard.isDone()) return;
            reporter.range(i, myPosts.size(), 5, 30, R.string.progress_posts);
            Post post = myPosts.get(i);
            if (!safeId(post.uid)) continue;
            Map<String, Object> data = new HashMap<>();
            data.put("text", post.text);
            data.put("timestamp", post.timestamp);
            data.put("visibility", post.visibility);
            long size = 300 + post.text.length();
            if (post.hasImage()) {
                String encoded = ImageStore.readBase64(app, post.image);
                if (encoded != null && encoded.length() <= MAX_IMAGE_CHARS) {
                    data.put(IMAGE_FIELD, encoded);
                    size += encoded.length();
                }
            }
            writer.set(owner.collection(POSTS).document(post.uid), data, size);
            postIds.add(post.uid);
            total++;
        }
        for (String stale : previousPosts) {
            if (!postIds.contains(stale) && safeId(stale)) {
                writer.delete(owner.collection(POSTS).document(stale));
            }
        }

        reporter.report(30, R.string.progress_profile);
        total += putMedia(app, owner, writer, "avatar", Profile.getAvatar(app));
        total += putMedia(app, owner, writer, "cover", Profile.getCover(app));

        clear(owner.collection(FRIENDS), writer);
        Set<String> peers = new LinkedHashSet<>();
        for (FriendStore.Friend friend : FriendStore.list(app, FriendStore.STATE_FRIEND)) {
            if (!safeId(friend.device)) continue;
            Map<String, Object> data = new HashMap<>();
            data.put("device", friend.device);
            data.put("name", friend.name);
            writer.set(owner.collection(FRIENDS).document(friend.device), data, 300);
            peers.add(friend.name);
            total++;
        }
        reporter.report(36, R.string.progress_profile);

        clear(owner.collection(REPLIES), writer);
        for (Map.Entry<String, MessageMetaStore.Reply> entry
                : MessageMetaStore.loadReplies(app).entrySet()) {
            if (!safeId(entry.getKey())) continue;
            Map<String, Object> data = new HashMap<>();
            data.put("replyId", entry.getValue().replyId);
            data.put("quote", entry.getValue().quote);
            writer.set(owner.collection(REPLIES).document(entry.getKey()), data, 500);
            total++;
        }
        reporter.report(40, R.string.progress_profile);

        clear(owner.collection(HEARTS), writer);
        for (Map.Entry<String, Set<String>> entry : MessageMetaStore.loadHearts(app).entrySet()) {
            for (String who : entry.getValue()) {
                if (!safeId(entry.getKey()) || !safeId(who)) continue;
                Map<String, Object> data = new HashMap<>();
                data.put("uid", entry.getKey());
                data.put("who", who);
                writer.set(owner.collection(HEARTS).document(entry.getKey() + "_" + who),
                        data, 300);
                total++;
            }
        }

        for (Row row : rows) {
            peers.add(row.peer);
        }

        clear(owner.collection(CHATS), writer);
        for (String peer : peers) {
            ChatStateStore.State state = ChatStateStore.get(app, peer);
            if (state == null || (!state.pinned && !state.muted)) continue;
            Map<String, Object> data = new HashMap<>();
            data.put("peer", peer);
            data.put("pinned", state.pinned);
            data.put("muted", state.muted);
            String chatId = UUID.nameUUIDFromBytes(peer.getBytes(StandardCharsets.UTF_8)).toString();
            writer.set(owner.collection(CHATS).document(chatId), data, 300);
            total++;
        }
        reporter.report(45, R.string.progress_messages);

        for (int i = 0; i < rows.size(); i++) {
            if (guard.isDone()) return;
            reporter.range(i, rows.size(), 45, 95, R.string.progress_messages);
            Row row = rows.get(i);
            if (!safeId(row.uid)) continue;
            Map<String, Object> data = toMap(app, row);
            if (data == null) continue;
            long size = 300 + row.text.length();
            Object image = data.get(IMAGE_FIELD);
            if (image instanceof String) {
                size += ((String) image).length();
            }
            writer.set(owner.collection(MESSAGES).document(row.uid), data, size);
            total++;
        }
        reporter.report(96, R.string.progress_finish);
        writer.flush();

        Map<String, Object> info = new HashMap<>();
        info.put("name", Profile.getName(app));
        info.put("bio", Profile.getBio(app));
        info.put("device", Profile.getId(app));
        info.put("updated", System.currentTimeMillis());
        info.put("postIds", postIds);
        await(owner.set(info));
        reporter.report(100, R.string.progress_finish);
        guard.succeed(total);
    }

    private static Set<String> previousPostIds(DocumentReference owner)
            throws ExecutionException, InterruptedException, TimeoutException {
        Set<String> ids = new HashSet<>();
        DocumentSnapshot snapshot = await(owner.get(Source.SERVER));
        Object raw = snapshot.get("postIds");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof String) ids.add((String) item);
            }
        }
        return ids;
    }

    private static int putMedia(Context app, DocumentReference owner, Writer writer, String id,
                                String file)
            throws ExecutionException, InterruptedException, TimeoutException {
        DocumentReference ref = owner.collection(MEDIA).document(id);
        String encoded = file == null ? null : ImageStore.readBase64(app, file);
        if (encoded == null || encoded.length() > MAX_IMAGE_CHARS) {
            writer.delete(ref);
            return 0;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("data", encoded);
        writer.set(ref, data, 300 + encoded.length());
        return 1;
    }

    private static void clear(CollectionReference collection, Writer writer)
            throws ExecutionException, InterruptedException, TimeoutException {
        QuerySnapshot snapshot = await(collection.get(Source.SERVER));
        for (DocumentSnapshot doc : snapshot) {
            writer.delete(doc.getReference());
        }
    }

    private static boolean safeId(String id) {
        return id != null && !id.isEmpty() && !id.contains("/") && !id.equals(".")
                && !id.equals("..");
    }

    private static <T> T await(Task<T> task)
            throws ExecutionException, InterruptedException, TimeoutException {
        return Tasks.await(task, AWAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static Map<String, Object> toMap(Context context, Row row) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", row.uid);
        data.put("peer", row.peer);
        data.put("sender", row.sender);
        data.put("text", row.text);
        data.put("timestamp", row.timestamp);
        data.put("status", row.status);
        if (row.image != null) {
            String encoded = ImageStore.readBase64(context, row.image);
            if (encoded == null || encoded.length() > MAX_IMAGE_CHARS) return null;
            data.put(IMAGE_FIELD, encoded);
        }
        return data;
    }

    public static void restore(Context context, Progress progress, Callback callback) {
        Context app = context.getApplicationContext();
        String id = accountId();
        if (id == null || !isOnline(app)) {
            callback.onError();
            return;
        }
        Guard guard = new Guard(callback, RESTORE_TIMEOUT_MS);
        Reporter reporter = new Reporter(progress);
        IO.execute(() -> {
            try {
                download(app, id, guard, reporter);
            } catch (ExecutionException | TimeoutException e) {
                Log.w(TAG, "Restore failed", e);
                guard.fail();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                guard.fail();
            } catch (RuntimeException e) {
                Log.w(TAG, "Restore crashed", e);
                guard.fail();
            }
        });
    }

    private static void download(Context app, String id, Guard guard, Reporter reporter)
            throws ExecutionException, InterruptedException, TimeoutException {
        reporter.report(2, R.string.progress_download);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference owner = db.collection(ROOT).document(id);
        DocumentSnapshot info = await(owner.get(Source.SERVER));
        reporter.report(8, R.string.progress_download);
        QuerySnapshot media = await(owner.collection(MEDIA).get(Source.SERVER));
        reporter.report(14, R.string.progress_download);
        QuerySnapshot posts = await(owner.collection(POSTS).get(Source.SERVER));
        reporter.report(20, R.string.progress_download);
        QuerySnapshot friends = await(owner.collection(FRIENDS).get(Source.SERVER));
        reporter.report(26, R.string.progress_download);
        QuerySnapshot chats = await(owner.collection(CHATS).get(Source.SERVER));
        reporter.report(32, R.string.progress_download);
        QuerySnapshot replies = await(owner.collection(REPLIES).get(Source.SERVER));
        reporter.report(38, R.string.progress_download);
        QuerySnapshot hearts = await(owner.collection(HEARTS).get(Source.SERVER));
        reporter.report(44, R.string.progress_download);
        QuerySnapshot messages = await(owner.collection(MESSAGES).get(Source.SERVER));
        reporter.report(50, R.string.progress_download);
        int found = media.size() + posts.size() + friends.size() + chats.size()
                + replies.size() + hearts.size() + messages.size();
        if (!info.exists() && found == 0) {
            guard.succeed(0);
            return;
        }
        reporter.report(52, R.string.progress_restore_profile);
        restoreProfile(app, info, media);
        reporter.report(55, R.string.progress_restore_posts);
        restorePosts(app, posts, reporter);
        reporter.report(72, R.string.progress_restore_friends);
        restoreFriends(app, friends);
        reporter.report(75, R.string.progress_restore_messages);
        insertMissing(app, messages, reporter);
        reporter.report(96, R.string.progress_finish);
        restoreMeta(app, replies, hearts);
        restoreChats(app, chats);
        reporter.report(100, R.string.progress_finish);
        guard.succeed(Math.max(found, 1));
    }

    private static void restoreProfile(Context app, DocumentSnapshot info, QuerySnapshot media) {
        String device = info.getString("device");
        if (device == null || device.isEmpty()) return;
        Profile.adoptId(app, device);
        String name = info.getString("name");
        if (name == null || name.isEmpty()) return;
        String bio = info.getString("bio");
        String avatar = null;
        String cover = null;
        for (QueryDocumentSnapshot doc : media) {
            String data = doc.getString("data");
            if (data == null || data.isEmpty()) continue;
            if ("avatar".equals(doc.getId())) {
                avatar = ImageStore.saveBase64(app, data);
            } else if ("cover".equals(doc.getId())) {
                cover = ImageStore.saveBase64(app, data);
            }
        }
        String oldAvatar = Profile.getAvatar(app);
        String oldCover = Profile.getCover(app);
        Profile.saveDetails(app, name, bio == null ? "" : bio, avatar, cover);
        deleteOld(app, oldAvatar, avatar);
        deleteOld(app, oldCover, cover);
    }

    private static void deleteOld(Context app, String oldFile, String newFile) {
        if (oldFile != null && !oldFile.equals(newFile)) {
            ImageStore.delete(app, oldFile);
        }
    }

    private static void restorePosts(Context app, QuerySnapshot posts, Reporter reporter) {
        int index = 0;
        int size = posts.size();
        for (QueryDocumentSnapshot doc : posts) {
            reporter.range(index++, size, 55, 72, R.string.progress_restore_posts);
            String text = doc.getString("text");
            Long time = doc.getLong("timestamp");
            Long visibility = doc.getLong("visibility");
            if (text == null || time == null) continue;
            String image = null;
            String data = doc.getString(IMAGE_FIELD);
            if (data != null && !data.isEmpty()) {
                image = ImageStore.saveBase64(app, data);
            }
            boolean added = PostStore.restore(app, doc.getId(), text, image, time,
                    visibility == null ? PostStorage.VISIBILITY_EVERYONE : visibility.intValue());
            if (!added && image != null) {
                ImageStore.delete(app, image);
            }
        }
    }

    private static void restoreFriends(Context app, QuerySnapshot friends) {
        for (QueryDocumentSnapshot doc : friends) {
            String device = doc.getString("device");
            String name = doc.getString("name");
            if (device == null || name == null || device.isEmpty() || name.isEmpty()) continue;
            if (!FriendStore.isFriend(app, device)) {
                FriendStore.upsert(app, device, name, FriendStore.STATE_FRIEND);
            }
        }
    }

    private static void restoreMeta(Context app, QuerySnapshot replies, QuerySnapshot hearts) {
        for (QueryDocumentSnapshot doc : replies) {
            String replyId = doc.getString("replyId");
            String quote = doc.getString("quote");
            if (replyId == null || quote == null) continue;
            MessageMetaStore.saveReply(app, doc.getId(), replyId, quote);
        }
        for (QueryDocumentSnapshot doc : hearts) {
            String uid = doc.getString("uid");
            String who = doc.getString("who");
            if (uid == null || who == null) continue;
            MessageMetaStore.setHeart(app, uid, who, true);
        }
    }

    private static void restoreChats(Context app, QuerySnapshot chats) {
        for (QueryDocumentSnapshot doc : chats) {
            String peer = doc.getString("peer");
            if (peer == null || peer.isEmpty()) continue;
            Boolean pinned = doc.getBoolean("pinned");
            Boolean muted = doc.getBoolean("muted");
            if (pinned != null && pinned) ChatStateStore.setPinned(app, peer, true);
            if (muted != null && muted) ChatStateStore.setMuted(app, peer, true);
        }
    }

    private static List<Row> readRows(Context context) {
        List<Row> rows = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                null,
                MessageStorage.COL_UID + " IS NOT NULL",
                null,
                MessageStorage.COL_ID + " ASC")) {
            if (cursor == null) return rows;
            int uidIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_UID);
            int peerIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_PEER);
            int senderIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_SENDER);
            int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
            int timeIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TIMESTAMP);
            int statusIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_STATUS);
            int imageIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_IMAGE);
            while (cursor.moveToNext()) {
                rows.add(new Row(
                        cursor.getString(uidIndex),
                        cursor.getString(peerIndex),
                        cursor.getString(senderIndex),
                        cursor.getString(textIndex),
                        cursor.getLong(timeIndex),
                        cursor.getInt(statusIndex),
                        cursor.getString(imageIndex)));
            }
        }
        return rows;
    }

    private static Set<String> existingUids(Context context) {
        Set<String> uids = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                new String[]{MessageStorage.COL_UID},
                MessageStorage.COL_UID + " IS NOT NULL",
                null,
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    uids.add(cursor.getString(0));
                }
            }
        }
        return uids;
    }

    private static int insertMissing(Context context, QuerySnapshot snapshot, Reporter reporter) {
        ContentResolver resolver = context.getContentResolver();
        Set<String> have = existingUids(context);
        Set<String> touchedPeers = new HashSet<>();
        int added = 0;
        int index = 0;
        int size = snapshot.size();
        for (QueryDocumentSnapshot doc : snapshot) {
            reporter.range(index++, size, 75, 95, R.string.progress_restore_messages);
            String uid = doc.getId();
            if (have.contains(uid)) continue;
            String peer = doc.getString("peer");
            String sender = doc.getString("sender");
            String text = doc.getString("text");
            Long time = doc.getLong("timestamp");
            Long status = doc.getLong("status");
            if (peer == null || sender == null || text == null || time == null) continue;
            String imageName = null;
            String imageData = doc.getString(IMAGE_FIELD);
            if (imageData != null && !imageData.isEmpty()) {
                imageName = ImageStore.saveBase64(context, imageData);
                if (imageName == null) continue;
            }
            ContentValues values = new ContentValues();
            values.put(MessageStorage.COL_SENDER, sender);
            values.put(MessageStorage.COL_TEXT, text);
            values.put(MessageStorage.COL_TIMESTAMP, time);
            values.put(MessageStorage.COL_PEER, peer);
            values.put(MessageStorage.COL_STATUS,
                    status == null ? MessageStorage.STATUS_DONE : status.intValue());
            values.put(MessageStorage.COL_UID, uid);
            values.put(MessageStorage.COL_IMAGE, imageName);
            if (resolver.insert(MessageStorage.CONTENT_URI, values) != null) {
                have.add(uid);
                touchedPeers.add(peer);
                added++;
            } else if (imageName != null) {
                ImageStore.delete(context, imageName);
            }
        }
        for (String peer : touchedPeers) {
            MessageSink.broadcastSaved(context, peer);
        }
        return added;
    }

    private static boolean isOnline(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager.getActiveNetwork();
        if (network == null) {
            Log.w(TAG, "No active network");
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        boolean online = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        Log.d(TAG, "Network available: " + online);
        return online;
    }
}