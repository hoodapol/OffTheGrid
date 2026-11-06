package com.example.offthegrid;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatStateStore {

    public interface Listener {
        void onChatStateChanged();
    }

    public static final class State {
        public final long lastRead;
        public final boolean pinned;
        public final boolean muted;
        public final long lastSeen;

        State(long lastRead, boolean pinned, boolean muted, long lastSeen) {
            this.lastRead = lastRead;
            this.pinned = pinned;
            this.muted = muted;
            this.lastSeen = lastSeen;
        }
    }

    private static final String DB_NAME = "chat_state.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "chat_state";
    private static final String PREFS = "chat_state_prefs";
    private static final String KEY_BASELINE = "baseline";

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Set<String> ONLINE = new HashSet<>();
    private static Helper helper;
    private static long baseline;
    private static boolean tracking;

    private ChatStateStore() {
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " (peer TEXT PRIMARY KEY, "
                    + "last_read INTEGER NOT NULL DEFAULT 0, "
                    + "pinned INTEGER NOT NULL DEFAULT 0, "
                    + "muted INTEGER NOT NULL DEFAULT 0, "
                    + "last_seen INTEGER NOT NULL DEFAULT 0)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private static synchronized Helper helper(Context context) {
        if (helper == null) {
            helper = new Helper(context.getApplicationContext());
        }
        return helper;
    }

    private static synchronized long baseline(Context context) {
        if (baseline == 0) {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            baseline = prefs.getLong(KEY_BASELINE, 0);
            if (baseline == 0) {
                baseline = System.currentTimeMillis();
                prefs.edit().putLong(KEY_BASELINE, baseline).apply();
            }
        }
        return baseline;
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyChanged() {
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                listener.onChatStateChanged();
            }
        });
    }

    public static State get(Context context, String peer) {
        long base = baseline(context);
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                TABLE, null, "peer=?", new String[]{peer}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return read(cursor, base);
            }
        }
        return new State(base, false, false, 0);
    }

    private static Map<String, State> all(Context context) {
        long base = baseline(context);
        Map<String, State> states = new HashMap<>();
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                TABLE, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                states.put(cursor.getString(cursor.getColumnIndexOrThrow("peer")),
                        read(cursor, base));
            }
        }
        return states;
    }

    private static State read(Cursor cursor, long base) {
        long lastRead = cursor.getLong(cursor.getColumnIndexOrThrow("last_read"));
        return new State(
                Math.max(lastRead, base),
                cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("muted")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("last_seen")));
    }

    private static void write(Context context, String peer, ContentValues values) {
        SQLiteDatabase db = helper(context).getWritableDatabase();
        int rows = db.update(TABLE, values, "peer=?", new String[]{peer});
        if (rows == 0) {
            values.put("peer", peer);
            db.insert(TABLE, null, values);
        }
    }

    public static void markRead(Context context, String peer) {
        ContentValues values = new ContentValues();
        values.put("last_read", System.currentTimeMillis());
        write(context, peer, values);
        notifyChanged();
    }

    public static void setPinned(Context context, String peer, boolean pinned) {
        ContentValues values = new ContentValues();
        values.put("pinned", pinned ? 1 : 0);
        write(context, peer, values);
        notifyChanged();
    }

    public static void setMuted(Context context, String peer, boolean muted) {
        ContentValues values = new ContentValues();
        values.put("muted", muted ? 1 : 0);
        write(context, peer, values);
        notifyChanged();
    }

    public static int countUnread(Context context, String peer, long lastRead) {
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                new String[]{MessageStorage.COL_ID},
                MessageStorage.COL_PEER + "=? AND " + MessageStorage.COL_SENDER + "=? AND "
                        + MessageStorage.COL_TIMESTAMP + ">?",
                new String[]{peer, peer, String.valueOf(lastRead)},
                null)) {
            return cursor == null ? 0 : cursor.getCount();
        }
    }

    public static int totalUnread(Context context) {
        Map<String, State> states = all(context);
        long base = baseline(context);
        int total = 0;
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                new String[]{MessageStorage.COL_PEER, MessageStorage.COL_TIMESTAMP},
                MessageStorage.COL_SENDER + "<>?",
                new String[]{MessageStorage.ME},
                null)) {
            if (cursor == null) return 0;
            int peerIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_PEER);
            int timeIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TIMESTAMP);
            while (cursor.moveToNext()) {
                State state = states.get(cursor.getString(peerIndex));
                if (state != null && state.muted) continue;
                long lastRead = state == null ? base : state.lastRead;
                if (cursor.getLong(timeIndex) > lastRead) {
                    total++;
                }
            }
        }
        return total;
    }

    public static boolean isMuted(Context context, String peer) {
        return get(context, peer).muted;
    }

    public static synchronized void startTracking(Context context) {
        if (tracking) return;
        tracking = true;
        Context app = context.getApplicationContext();
        ONLINE.addAll(PeerRegistry.names());
        PeerRegistry.addListener(() -> WORKER.execute(() -> onPeersChanged(app)));
    }

    private static synchronized void onPeersChanged(Context context) {
        List<String> now = PeerRegistry.names();
        long time = System.currentTimeMillis();
        Set<String> touched = new HashSet<>(now);
        for (String peer : ONLINE) {
            if (!now.contains(peer)) touched.add(peer);
        }
        for (String peer : touched) {
            ContentValues values = new ContentValues();
            values.put("last_seen", time);
            write(context, peer, values);
        }
        ONLINE.clear();
        ONLINE.addAll(now);
        notifyChanged();
    }
}