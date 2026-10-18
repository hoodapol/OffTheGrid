package com.example.offthegrid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ChatDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "offthegrid.db";
    private static final int DB_VERSION = 1;

    public ChatDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + MessageStorage.TABLE + " ("
                + MessageStorage.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + MessageStorage.COL_SENDER + " TEXT NOT NULL, "
                + MessageStorage.COL_TEXT + " TEXT NOT NULL, "
                + MessageStorage.COL_TIMESTAMP + " INTEGER NOT NULL, "
                + MessageStorage.COL_PEER + " TEXT NOT NULL, "
                + MessageStorage.COL_STATUS + " INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + MessageStorage.TABLE);
        onCreate(db);
    }

    public long insertMessage(String sender, String peer, String text, int status) {
        ContentValues values = new ContentValues();
        values.put(MessageStorage.COL_SENDER, sender);
        values.put(MessageStorage.COL_TEXT, text);
        values.put(MessageStorage.COL_TIMESTAMP, System.currentTimeMillis());
        values.put(MessageStorage.COL_PEER, peer);
        values.put(MessageStorage.COL_STATUS, status);
        return getWritableDatabase().insert(MessageStorage.TABLE, null, values);
    }

    public List<Message> getMessages(String peer) {
        List<Message> list = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                MessageStorage.TABLE,
                null,
                MessageStorage.COL_PEER + "=?",
                new String[]{peer},
                null,
                null,
                MessageStorage.COL_TIMESTAMP + " ASC, " + MessageStorage.COL_ID + " ASC")) {
            int senderIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_SENDER);
            int textIndex = cursor.getColumnIndexOrThrow(MessageStorage.COL_TEXT);
            while (cursor.moveToNext()) {
                boolean sent = MessageStorage.ME.equals(cursor.getString(senderIndex));
                list.add(new Message(cursor.getString(textIndex), sent));
            }
        }
        return list;
    }
}