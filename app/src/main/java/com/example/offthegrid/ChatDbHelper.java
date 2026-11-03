package com.example.offthegrid;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ChatDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "offthegrid.db";
    private static final int DB_VERSION = 3;

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
    }
}