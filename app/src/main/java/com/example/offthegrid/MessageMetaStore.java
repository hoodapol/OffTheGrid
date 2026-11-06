package com.example.offthegrid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MessageMetaStore {

    public static final String HEART = "❤️";

    public static final class Reply {
        public final String replyId;
        public final String quote;

        Reply(String replyId, String quote) {
            this.replyId = replyId;
            this.quote = quote;
        }
    }

    private static final String DB_NAME = "message_meta.db";
    private static final int DB_VERSION = 1;
    private static final String REPLIES = "replies";
    private static final String REACTIONS = "reactions";

    private static Helper helper;

    private MessageMetaStore() {
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + REPLIES + " (uid TEXT PRIMARY KEY, "
                    + "reply_id TEXT NOT NULL, quote TEXT NOT NULL)");
            db.execSQL("CREATE TABLE " + REACTIONS + " (uid TEXT NOT NULL, "
                    + "who TEXT NOT NULL, emoji TEXT NOT NULL, PRIMARY KEY (uid, who))");
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

    public static void saveReply(Context context, String uid, String replyId, String quote) {
        ContentValues values = new ContentValues();
        values.put("uid", uid);
        values.put("reply_id", replyId);
        values.put("quote", quote);
        helper(context).getWritableDatabase().insertWithOnConflict(
                REPLIES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static Reply getReply(Context context, String uid) {
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                REPLIES, null, "uid=?", new String[]{uid}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return new Reply(
                        cursor.getString(cursor.getColumnIndexOrThrow("reply_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("quote")));
            }
        }
        return null;
    }

    public static Map<String, Reply> loadReplies(Context context) {
        Map<String, Reply> replies = new HashMap<>();
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                REPLIES, null, null, null, null, null, null)) {
            int uidIndex = cursor.getColumnIndexOrThrow("uid");
            int idIndex = cursor.getColumnIndexOrThrow("reply_id");
            int quoteIndex = cursor.getColumnIndexOrThrow("quote");
            while (cursor.moveToNext()) {
                replies.put(cursor.getString(uidIndex),
                        new Reply(cursor.getString(idIndex), cursor.getString(quoteIndex)));
            }
        }
        return replies;
    }

    public static void setHeart(Context context, String uid, String who, boolean on) {
        SQLiteDatabase db = helper(context).getWritableDatabase();
        if (on) {
            ContentValues values = new ContentValues();
            values.put("uid", uid);
            values.put("who", who);
            values.put("emoji", HEART);
            db.insertWithOnConflict(REACTIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            db.delete(REACTIONS, "uid=? AND who=?", new String[]{uid, who});
        }
    }

    public static Map<String, Set<String>> loadHearts(Context context) {
        Map<String, Set<String>> hearts = new HashMap<>();
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                REACTIONS, null, null, null, null, null, null)) {
            int uidIndex = cursor.getColumnIndexOrThrow("uid");
            int whoIndex = cursor.getColumnIndexOrThrow("who");
            while (cursor.moveToNext()) {
                String uid = cursor.getString(uidIndex);
                Set<String> people = hearts.get(uid);
                if (people == null) {
                    people = new HashSet<>();
                    hearts.put(uid, people);
                }
                people.add(cursor.getString(whoIndex));
            }
        }
        return hearts;
    }

    public static List<String> heartedBy(Context context, String uid) {
        List<String> people = new ArrayList<>();
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                REACTIONS, new String[]{"who"}, "uid=?", new String[]{uid},
                null, null, null)) {
            while (cursor.moveToNext()) {
                people.add(cursor.getString(0));
            }
        }
        return people;
    }

    public static int heartCount(Context context, String uid) {
        return heartedBy(context, uid).size();
    }

    public static boolean isHeartedBy(Context context, String uid, String who) {
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                REACTIONS, new String[]{"who"}, "uid=? AND who=?", new String[]{uid, who},
                null, null, null)) {
            return cursor.moveToFirst();
        }
    }

    public static boolean messageExists(Context context, String peer, String uid) {
        try (Cursor cursor = context.getContentResolver().query(
                MessageStorage.CONTENT_URI,
                new String[]{MessageStorage.COL_ID},
                MessageStorage.COL_UID + "=? AND " + MessageStorage.COL_PEER + "=?",
                new String[]{uid, peer},
                null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }
}