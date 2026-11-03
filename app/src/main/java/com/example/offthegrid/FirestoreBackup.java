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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirestoreBackup {

    public interface Callback {
        void onDone(int count);

        void onError();
    }

    private static final String TAG = "FirestoreBackup";
    private static final String ROOT = "backups";
    private static final String MESSAGES = "messages";
    private static final int BATCH_SIZE = 400;
    private static final long TIMEOUT_MS = 20000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FirestoreBackup() {
    }

    private static final class Guard {
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final Callback callback;
        private final Runnable timeout = this::fail;

        Guard(Callback callback) {
            this.callback = callback;
            MAIN.postDelayed(timeout, TIMEOUT_MS);
        }

        void succeed(int count) {
            if (done.compareAndSet(false, true)) {
                MAIN.removeCallbacks(timeout);
                MAIN.post(() -> callback.onDone(count));
            }
        }

        void fail() {
            if (done.compareAndSet(false, true)) {
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
        Guard guard = new Guard(callback);
        IO.execute(() -> {
            List<Map<String, Object>> rows = readRows(app);
            if (rows.isEmpty()) {
                guard.succeed(0);
                return;
            }
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentReference owner = db.collection(ROOT).document(Profile.getId(app));
            List<Task<Void>> tasks = new ArrayList<>();
            for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
                WriteBatch batch = db.batch();
                if (start == 0) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", Profile.getName(app));
                    info.put("updated", System.currentTimeMillis());
                    batch.set(owner, info);
                }
                int end = Math.min(start + BATCH_SIZE, rows.size());
                for (Map<String, Object> row : rows.subList(start, end)) {
                    batch.set(owner.collection(MESSAGES).document((String) row.get("uid")), row);
                }
                tasks.add(batch.commit());
            }
            Tasks.whenAll(tasks)
                    .addOnSuccessListener(unused -> guard.succeed(rows.size()))
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Backup failed", e);
                        guard.fail();
                    });
        });
    }

    public static void restore(Context context, String code, Callback callback) {
        Context app = context.getApplicationContext();
        if (code.isEmpty() || code.contains("/") || !isOnline(app)) {
            callback.onError();
            return;
        }
        Guard guard = new Guard(callback);
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

    private static List<Map<String, Object>> readRows(Context context) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                null,
                MessageStorage.COL_UID + " IS NOT NULL AND " + MessageStorage.COL_IMAGE + " IS NULL",
                null,
                MessageStorage.COL_ID + " ASC")) {
            if (cursor == null) return rows;
            int uidIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_UID);
            int peerIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_PEER);
            int senderIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_SENDER);
            int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
            int timeIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TIMESTAMP);
            int statusIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_STATUS);
            while (cursor.moveToNext()) {
                Map<String, Object> row = new HashMap<>();
                row.put("uid", cursor.getString(uidIndex));
                row.put("peer", cursor.getString(peerIndex));
                row.put("sender", cursor.getString(senderIndex));
                row.put("text", cursor.getString(textIndex));
                row.put("timestamp", cursor.getLong(timeIndex));
                row.put("status", cursor.getInt(statusIndex));
                rows.add(row);
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
            ContentValues values = new ContentValues();
            values.put(MessageStorage.COL_SENDER, sender);
            values.put(MessageStorage.COL_TEXT, text);
            values.put(MessageStorage.COL_TIMESTAMP, time);
            values.put(MessageStorage.COL_PEER, peer);
            values.put(MessageStorage.COL_STATUS,
                    status == null ? MessageStorage.STATUS_DONE : status.intValue());
            values.put(MessageStorage.COL_UID, uid);
            if (resolver.insert(MessageStorage.CONTENT_URI, values) != null) {
                have.add(uid);
                touchedPeers.add(peer);
                added++;
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
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}