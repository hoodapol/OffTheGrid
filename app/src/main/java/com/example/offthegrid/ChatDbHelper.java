package com.example.offthegrid;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ChatDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "offthegrid.db";
    private static final int DB_VERSION = 4;

    private static ChatDbHelper instance;

    public static synchronized ChatDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ChatDbHelper(context.getApplicationContext());
        }
        return instance;
    }

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
                + MessageStorage.COL_STATUS + " INTEGER NOT NULL DEFAULT 0, "
                + MessageStorage.COL_UID + " TEXT, "
                + MessageStorage.COL_IMAGE + " TEXT)");
        createPosts(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + MessageStorage.TABLE
                    + " ADD COLUMN " + MessageStorage.COL_UID + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + MessageStorage.TABLE
                    + " ADD COLUMN " + MessageStorage.COL_IMAGE + " TEXT");
        }
        if (oldVersion < 4) {
            createPosts(db);
        }
    }

    private static void createPosts(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + PostStorage.TABLE + " ("
                + PostStorage.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + PostStorage.COL_UID + " TEXT NOT NULL UNIQUE, "
                + PostStorage.COL_OWNER + " TEXT NOT NULL, "
                + PostStorage.COL_TEXT + " TEXT NOT NULL, "
                + PostStorage.COL_IMAGE + " TEXT, "
                + PostStorage.COL_TIMESTAMP + " INTEGER NOT NULL, "
                + PostStorage.COL_VISIBILITY + " INTEGER NOT NULL DEFAULT 0)");
    }
}