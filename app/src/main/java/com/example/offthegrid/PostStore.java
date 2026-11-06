package com.example.offthegrid;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PostStore {

    private static final String EDIT_PREFS = "post_edits";
    private static final String EDIT_KEY = "edited";

    private PostStore() {
    }

    private static ContentValues values(String uid, String owner, String text, String image,
                                        long timestamp, int visibility) {
        ContentValues values = new ContentValues();
        values.put(PostStorage.COL_UID, uid);
        values.put(PostStorage.COL_OWNER, owner);
        values.put(PostStorage.COL_TEXT, text);
        values.put(PostStorage.COL_IMAGE, image);
        values.put(PostStorage.COL_TIMESTAMP, timestamp);
        values.put(PostStorage.COL_VISIBILITY, visibility);
        return values;
    }

    private static Set<String> editedSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(EDIT_PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(EDIT_KEY, new HashSet<>()));
    }

    private static void saveEditedSet(Context context, Set<String> set) {
        context.getSharedPreferences(EDIT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(EDIT_KEY, set)
                .apply();
    }

    public static boolean isEdited(Context context, String uid) {
        return editedSet(context).contains(uid);
    }

    public static Uri add(Context context, String text, String image, int visibility) {
        ContentValues values = values(UUID.randomUUID().toString(), Profile.getId(context),
                text, image, System.currentTimeMillis(), visibility);
        return context.getContentResolver().insert(PostStorage.CONTENT_URI, values);
    }

    public static boolean update(Context context, Post post, String text, int visibility) {
        context.getContentResolver().delete(
                PostStorage.CONTENT_URI,
                PostStorage.COL_ID + "=?",
                new String[]{String.valueOf(post.id)});
        ContentValues edited = values(post.uid, post.owner, text, post.image,
                post.timestamp, visibility);
        if (context.getContentResolver().insert(PostStorage.CONTENT_URI, edited) != null) {
            if (!text.equals(post.text)) {
                Set<String> set = editedSet(context);
                set.add(post.uid);
                saveEditedSet(context, set);
            }
            return true;
        }
        ContentValues original = values(post.uid, post.owner, post.text, post.image,
                post.timestamp, post.visibility);
        context.getContentResolver().insert(PostStorage.CONTENT_URI, original);
        return false;
    }

    public static boolean restore(Context context, String uid, String text, String image,
                                  long timestamp, int visibility) {
        try (Cursor cursor = context.getContentResolver().query(
                PostStorage.CONTENT_URI,
                new String[]{PostStorage.COL_ID},
                PostStorage.COL_UID + "=?",
                new String[]{uid},
                null)) {
            if (cursor != null && cursor.moveToFirst()) return false;
        }
        ContentValues values = values(uid, Profile.getId(context), text, image,
                timestamp, visibility);
        return context.getContentResolver().insert(PostStorage.CONTENT_URI, values) != null;
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
        Set<String> set = editedSet(context);
        if (set.remove(post.uid)) {
            saveEditedSet(context, set);
        }
    }
}