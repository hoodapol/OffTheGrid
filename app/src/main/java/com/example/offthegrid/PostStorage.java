package com.example.offthegrid;

import android.net.Uri;

public final class PostStorage {
    private PostStorage() {
    }

    public static final String TABLE = "posts";
    public static final String COL_ID = "_id";
    public static final String COL_UID = "uid";
    public static final String COL_OWNER = "owner";
    public static final String COL_TEXT = "text";
    public static final String COL_IMAGE = "image";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_VISIBILITY = "visibility";

    public static final int VISIBILITY_EVERYONE = 0;
    public static final int VISIBILITY_FRIENDS = 1;

    public static final String AUTHORITY = "com.example.offthegrid.postprovider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE);
}