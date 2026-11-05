package com.example.offthegrid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PostStore {

    private PostStore() {
    }

    public static Uri add(Context context, String text, String image, int visibility) {
        ContentValues values = new ContentValues();
        values.put(PostStorage.COL_UID, UUID.randomUUID().toString());
        values.put(PostStorage.COL_OWNER, Profile.getId(context));
        values.put(PostStorage.COL_TEXT, text);
        values.put(PostStorage.COL_IMAGE, image);
        values.put(PostStorage.COL_TIMESTAMP, System.currentTimeMillis());
        values.put(PostStorage.COL_VISIBILITY, visibility);
        return context.getContentResolver().insert(PostStorage.CONTENT_URI, values);
    }

    public static List<Post> loadMine(Context context) {
        List<Post> list = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                PostStorage.CONTENT_URI,
                null,
                PostStorage.COL_OWNER + "=?",
                new String[]{Profile.getId(context)},
                PostStorage.COL_TIMESTAMP + " DESC, " + PostStorage.COL_ID + " DESC")) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndexOrThrow(PostStorage.COL_ID);
                int uidIndex = cursor.getColumnIndexOrThrow(PostStorage.COL_UID);
                int ownerIndex = cursor.getColumnIndexOrThrow(PostStorage.COL_OWNER);
                int textIndex = cursor.getColumnIndexOrThrow(PostStorage.COL_TEXT);
                int imageIndex = cursor.getColumnIndexOrThrow(PostStorage.COL_IMAGE);
                int timeIndex = cursor.getColumnIndexOrThrow(PostStorage.COL_TIMESTAMP);
                int visibilityIndex = cursor.getColumnIndexOrThrow(PostStorage.COL_VISIBILITY);
                while (cursor.moveToNext()) {
                    list.add(new Post(
                            cursor.getLong(idIndex),
                            cursor.getString(uidIndex),
                            cursor.getString(ownerIndex),
                            cursor.getString(textIndex),
                            cursor.getString(imageIndex),
                            cursor.getLong(timeIndex),
                            cursor.getInt(visibilityIndex)));
                }
            }
        }
        return list;
    }

    public static void delete(Context context, Post post) {
        context.getContentResolver().delete(
                PostStorage.CONTENT_URI,
                PostStorage.COL_ID + "=?",
                new String[]{String.valueOf(post.id)});
        if (post.hasImage()) {
            ImageStore.delete(context, post.image);
        }
    }
}