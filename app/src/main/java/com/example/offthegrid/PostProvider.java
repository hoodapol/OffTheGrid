package com.example.offthegrid;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PostProvider extends ContentProvider {
    private static final int POSTS = 1;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(PostStorage.AUTHORITY, PostStorage.TABLE, POSTS);
    }

    private ChatDbHelper helper;

    @Override
    public boolean onCreate() {
        helper = ChatDbHelper.getInstance(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (MATCHER.match(uri) != POSTS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        Cursor cursor = helper.getReadableDatabase().query(PostStorage.TABLE, projection,
                selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (MATCHER.match(uri) != POSTS || values == null) {
            throw new IllegalArgumentException("Cannot insert into " + uri);
        }
        long id = helper.getWritableDatabase().insert(PostStorage.TABLE, null, values);
        if (id < 0) return null;
        getContext().getContentResolver().notifyChange(PostStorage.CONTENT_URI, null);
        return ContentUris.withAppendedId(PostStorage.CONTENT_URI, id);
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        if (MATCHER.match(uri) != POSTS || values == null) {
            throw new IllegalArgumentException("Cannot update " + uri);
        }
        int count = helper.getWritableDatabase()
                .update(PostStorage.TABLE, values, selection, selectionArgs);
        if (count > 0) {
            getContext().getContentResolver().notifyChange(PostStorage.CONTENT_URI, null);
        }
        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        if (MATCHER.match(uri) != POSTS) {
            throw new IllegalArgumentException("Cannot delete from " + uri);
        }
        int count = helper.getWritableDatabase()
                .delete(PostStorage.TABLE, selection, selectionArgs);
        if (count > 0) {
            getContext().getContentResolver().notifyChange(PostStorage.CONTENT_URI, null);
        }
        return count;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        if (MATCHER.match(uri) == POSTS) {
            return "vnd.android.cursor.dir/vnd.com.example.offthegrid.post";
        }
        return null;
    }
}