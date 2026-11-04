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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final String TAG = "FirestoreBackup";
    private static final String ROOT = "backups";
    private static final String MESSAGES = "messages";
    private static final String IMAGE_FIELD = "imageData";
    private static final int BATCH_SIZE = 400;
    private static final long BATCH_BYTES = 3L * 1024 * 1024;
    private static final int MAX_IMAGE_CHARS = 900000;
    private static final long AWAIT_SECONDS = 30;
    private static final long BACKUP_TIMEOUT_MS = 300000;
    private static final long RESTORE_TIMEOUT_MS = 90000;
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

    public static void backup(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (!isOnline(app)) {
            callback.onError();
            return;
        }
        Guard guard = new Guard(callback, BACKUP_TIMEOUT_MS);
        IO.execute(() -> {
            try {
                upload(app, guard);
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

    private static void upload(Context app, Guard guard)
            throws ExecutionException, InterruptedException, TimeoutException {
        List<Row> rows = readRows(app);
        Log.d(TAG, "Rows to back up: " + rows.size());
        if (rows.isEmpty()) {
            guard.succeed(0);
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference owner = db.collection(ROOT).document(Profile.getId(app));
        Map<String, Object> info = new HashMap<>();
        info.put("name", Profile.getName(app));
        info.put("updated", System.currentTimeMillis());
        await(owner.set(info));

        WriteBatch batch = db.batch();
        int inBatch = 0;
        long bytes = 0;
        int total = 0;
        for (Row row : rows) {
            if (guard.isDone()) return;
            Map<String, Object> data = toMap(app, row);
            if (data == null) continue;
            long size = 300 + row.text.length();
            Object image = data.get(IMAGE_FIELD);
            if (image instanceof String) {
                size += ((String) image).length();
            }
            if (inBatch > 0 && (inBatch >= BATCH_SIZE || bytes + size > BATCH_BYTES)) {
                await(batch.commit());
                batch = db.batch();
                inBatch = 0;
                bytes = 0;
            }
            batch.set(owner.collection(MESSAGES).document(row.uid), data);
            inBatch++;
            bytes += size;
            total++;
        }
        if (inBatch > 0) {
            await(batch.commit());
        }
        guard.succeed(total);
    }

    private static void await(Task<Void> task)
            throws ExecutionException, InterruptedException, TimeoutException {
        Tasks.await(task, AWAIT_SECONDS, TimeUnit.SECONDS);
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

    public static void restore(Context context, String code, Callback callback) {
        Context app = context.getApplicationContext();
        if (code.isEmpty() || code.contains("/") || !isOnline(app)) {
            callback.onError();
            return;
        }
        Guard guard = new Guard(callback, RESTORE_TIMEOUT_MS);
        FirebaseFirestore.getInstance()
                .collection(ROOT).document(code).collection(MESSAGES)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot ->
                        IO.execute(() -> guard.succeed(insertMissing(app, snapshot))))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Restore failed", e);
                    guard.fail();
                });
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

    private static int insertMissing(Context context, QuerySnapshot snapshot) {
        ContentResolver resolver = context.getContentResolver();
        Set<String> have = existingUids(context);
        Set<String> touchedPeers = new HashSet<>();
        int added = 0;
        for (QueryDocumentSnapshot doc : snapshot) {
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