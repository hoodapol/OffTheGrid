package com.example.offthegrid;
import android.net.Uri;
public final class MessageStorage {
    private MessageStorage() {
    }

    public static final String TABLE = "messages";
    public static final String COL_ID = "_id";
    public static final String COL_SENDER = "sender";
    public static final String COL_TEXT = "text";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_PEER = "peer";
    public static final String COL_STATUS = "status";

    public static final int STATUS_DONE = 0;
    public static final int STATUS_PENDING = 1;

    public static final String ME = "Me";

    public static final String AUTHORITY = "com.example.offthegrid.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE);
}