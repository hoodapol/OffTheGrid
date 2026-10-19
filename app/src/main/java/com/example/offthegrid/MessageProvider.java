package com.example.offthegrid;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MessageProvider extends ContentProvider {
    private static final int MESSAGES = 1;
    private static final int MESSAGE_ID = 2;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(MessageStorage.AUTHORITY, MessageStorage.TABLE, MESSAGES);
        MATCHER.addURI(MessageStorage.AUTHORITY, MessageStorage.TABLE + "/#", MESSAGE_ID);
    }

    private ChatDbHelper helper;

    @Override
    public boolean onCreate() {
        helper = new ChatDbHelper(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor;
        switch (MATCHER.match(uri)) {
            case MESSAGES:
                cursor = db.query(MessageStorage.TABLE, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case MESSAGE_ID:
                cursor = db.query(MessageStorage.TABLE, projection, MessageStorage.COL_ID + "=?",
                        new String[]{uri.getLastPathSegment()}, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (MATCHER.match(uri) != MESSAGES || values == null) {
            throw new IllegalArgumentException("Cannot insert into " + uri);
        }
        long id = helper.getWritableDatabase().insert(MessageStorage.TABLE, null, values);
        if (id < 0) return null;
        getContext().getContentResolver().notifyChange(MessageStorage.CONTENT_URI, null);
        return ContentUris.withAppendedId(MessageStorage.CONTENT_URI, id);
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        if (MATCHER.match(uri) != MESSAGES || values == null) {
            throw new IllegalArgumentException("Cannot update " + uri);
        }
        int count = helper.getWritableDatabase()
                .update(MessageStorage.TABLE, values, selection, selectionArgs);
        if (count > 0) {
            getContext().getContentResolver().notifyChange(MessageStorage.CONTENT_URI, null);
        }
        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        if (MATCHER.match(uri) != MESSAGES) {
            throw new IllegalArgumentException("Cannot delete from " + uri);
        }
        int count = helper.getWritableDatabase()
                .delete(MessageStorage.TABLE, selection, selectionArgs);
        if (count > 0) {
            getContext().getContentResolver().notifyChange(MessageStorage.CONTENT_URI, null);
        }
        return count;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case MESSAGES:
                return "vnd.android.cursor.dir/vnd.com.example.offthegrid.message";
            case MESSAGE_ID:
                return "vnd.android.cursor.item/vnd.com.example.offthegrid.message";
            default:
                return null;
        }
    }
}