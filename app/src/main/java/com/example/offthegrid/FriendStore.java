package com.example.offthegrid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FriendStore {

    public static final int STATE_NONE = -1;
    public static final int STATE_PENDING_OUT = 0;
    public static final int STATE_PENDING_IN = 1;
    public static final int STATE_FRIEND = 2;

    public interface Listener {
        void onFriendsChanged();
    }

    public static final class Friend {
        public final String device;
        public final String name;
        public final int state;
        public final String bio;
        public final String avatar;
        public final String cover;
        public final long synced;

        Friend(String device, String name, int state, String bio, String avatar, String cover,
               long synced) {
            this.device = device;
            this.name = name;
            this.state = state;
            this.bio = bio;
            this.avatar = avatar;
            this.cover = cover;
            this.synced = synced;
        }
    }

    private static final String DB_NAME = "friends.db";
    private static final int DB_VERSION = 1;
    private static final String FRIENDS = "friends";
    private static final String POSTS = "friend_posts";

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Helper helper;

    private FriendStore() {
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + FRIENDS + " (device TEXT PRIMARY KEY, "
                    + "name TEXT NOT NULL, state INTEGER NOT NULL, bio TEXT, avatar TEXT, "
                    + "cover TEXT, synced INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE " + POSTS + " (uid TEXT PRIMARY KEY, "
                    + "owner TEXT NOT NULL, text TEXT, image TEXT, "
                    + "timestamp INTEGER NOT NULL, visibility INTEGER NOT NULL DEFAULT 0)");
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

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyChanged() {
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                listener.onFriendsChanged();
            }
        });
    }

    public static Friend get(Context context, String device) {
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                FRIENDS, null, "device=?", new String[]{device}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    public static Friend findByName(Context context, String name) {
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                FRIENDS, null, "name=?", new String[]{name}, null, null, "state DESC", "1")) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    public static int stateOf(Context context, String device) {
        Friend friend = get(context, device);
        return friend == null ? STATE_NONE : friend.state;
    }

    public static boolean isFriend(Context context, String device) {
        return stateOf(context, device) == STATE_FRIEND;
    }

    public static List<Friend> list(Context context, int state) {
        List<Friend> friends = new ArrayList<>();
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                FRIENDS, null, "state=?", new String[]{String.valueOf(state)},
                null, null, "name COLLATE NOCASE")) {
            while (cursor.moveToNext()) {
                friends.add(read(cursor));
            }
        }
        return friends;
    }

    public static int count(Context context) {
        return (int) DatabaseUtils.queryNumEntries(
                helper(context).getReadableDatabase(), FRIENDS, "state=?",
                new String[]{String.valueOf(STATE_FRIEND)});
    }

    public static void upsert(Context context, String device, String name, int state) {
        SQLiteDatabase db = helper(context).getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("state", state);
        int rows = db.update(FRIENDS, values, "device=?", new String[]{device});
        if (rows == 0) {
            values.put("device", device);
            db.insert(FRIENDS, null, values);
        }
        notifyChanged();
    }

    public static void remove(Context context, String device) {
        SQLiteDatabase db = helper(context).getWritableDatabase();
        Friend friend = get(context, device);
        if (friend != null) {
            deleteFile(context, friend.avatar);
            deleteFile(context, friend.cover);
        }
        for (String image : postImages(db, device)) {
            deleteFile(context, image);
        }
        db.delete(POSTS, "owner=?", new String[]{device});
        db.delete(FRIENDS, "device=?", new String[]{device});
        notifyChanged();
    }

    public static void saveCache(Context context, String device, RemoteProfile profile) {
        Friend friend = get(context, device);
        if (friend == null || friend.state != STATE_FRIEND) return;
        SQLiteDatabase db = helper(context).getWritableDatabase();
        List<String> oldFiles = postImages(db, device);
        if (friend.avatar != null) oldFiles.add(friend.avatar);
        if (friend.cover != null) oldFiles.add(friend.cover);

        String avatar = storeCopy(context, profile.avatar);
        String cover = storeCopy(context, profile.cover);
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("bio", profile.bio);
            values.put("avatar", avatar);
            values.put("cover", cover);
            values.put("synced", System.currentTimeMillis());
            db.update(FRIENDS, values, "device=?", new String[]{device});
            db.delete(POSTS, "owner=?", new String[]{device});
            for (RemoteProfile.RemotePost post : profile.posts) {
                ContentValues row = new ContentValues();
                row.put("uid", device + ":" + post.id);
                row.put("owner", device);
                row.put("text", post.text);
                row.put("image", storeCopy(context, post.image));
                row.put("timestamp", post.timestamp);
                row.put("visibility", post.visibility);
                db.insertWithOnConflict(POSTS, null, row, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        for (String file : oldFiles) {
            deleteFile(context, file);
        }
        notifyChanged();
    }

    public static RemoteProfile loadCache(Context context, Friend friend) {
        Bitmap avatar = friend.avatar == null ? null : ImageStore.thumbnail(context, friend.avatar);
        Bitmap cover = friend.cover == null ? null : ImageStore.thumbnail(context, friend.cover);
        List<RemoteProfile.RemotePost> posts = new ArrayList<>();
        try (Cursor cursor = helper(context).getReadableDatabase().query(
                POSTS, null, "owner=?", new String[]{friend.device}, null, null,
                "timestamp DESC")) {
            int uidIndex = cursor.getColumnIndexOrThrow("uid");
            int textIndex = cursor.getColumnIndexOrThrow("text");
            int imageIndex = cursor.getColumnIndexOrThrow("image");
            int timeIndex = cursor.getColumnIndexOrThrow("timestamp");
            int visibilityIndex = cursor.getColumnIndexOrThrow("visibility");
            while (cursor.moveToNext()) {
                String text = cursor.isNull(textIndex) ? "" : cursor.getString(textIndex);
                String image = cursor.isNull(imageIndex) ? null : cursor.getString(imageIndex);
                Bitmap bitmap = image == null ? null : ImageStore.thumbnail(context, image);
                if (text.isEmpty() && bitmap == null) continue;
                posts.add(new RemoteProfile.RemotePost(cursor.getString(uidIndex), text, bitmap,
                        cursor.getLong(timeIndex), cursor.getInt(visibilityIndex)));
            }
        }
        return RemoteProfile.of(friend.name, friend.device,
                friend.bio == null ? "" : friend.bio, avatar, cover, posts);
    }

    private static String storeCopy(Context context, Bitmap bitmap) {
        if (bitmap == null) return null;
        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (copy == null) return null;
        return ImageStore.saveBitmap(context, copy);
    }

    private static List<String> postImages(SQLiteDatabase db, String device) {
        List<String> images = new ArrayList<>();
        try (Cursor cursor = db.query(POSTS, new String[]{"image"},
                "owner=? AND image IS NOT NULL", new String[]{device}, null, null, null)) {
            while (cursor.moveToNext()) {
                images.add(cursor.getString(0));
            }
        }
        return images;
    }

    private static void deleteFile(Context context, String name) {
        if (name != null && !name.isEmpty()) {
            ImageStore.delete(context, name);
        }
    }

    private static Friend read(Cursor cursor) {
        return new Friend(
                cursor.getString(cursor.getColumnIndexOrThrow("device")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getInt(cursor.getColumnIndexOrThrow("state")),
                cursor.getString(cursor.getColumnIndexOrThrow("bio")),
                cursor.getString(cursor.getColumnIndexOrThrow("avatar")),
                cursor.getString(cursor.getColumnIndexOrThrow("cover")),
                cursor.getLong(cursor.getColumnIndexOrThrow("synced")));
    }
}