package com.example.offthegrid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RemoteProfile {

    private static final int MAX_POSTS = 10;
    private static final int MAX_IMAGE_CHARS = 400000;
    private static final int MAX_TEXT = 500;
    private static final int MAX_BIO = 300;

    public static final class RemotePost {
        public final String id;
        public final String text;
        public final Bitmap image;
        public final long timestamp;
        public final int visibility;
        public int hearts;
        public boolean liked;

        public RemotePost(String id, String text, Bitmap image, long timestamp, int visibility) {
            this(id, text, image, timestamp, visibility, 0, false);
        }

        public RemotePost(String id, String text, Bitmap image, long timestamp, int visibility,
                          int hearts, boolean liked) {
            this.id = id;
            this.text = text;
            this.image = image;
            this.timestamp = timestamp;
            this.visibility = visibility;
            this.hearts = hearts;
            this.liked = liked;
        }
    }

    public final String name;
    public final String device;
    public final String bio;
    public final Bitmap avatar;
    public final Bitmap cover;
    public final List<RemotePost> posts;

    private RemoteProfile(String name, String device, String bio, Bitmap avatar, Bitmap cover,
                          List<RemotePost> posts) {
        this.name = name;
        this.device = device;
        this.bio = bio;
        this.avatar = avatar;
        this.cover = cover;
        this.posts = posts;
    }

    public static RemoteProfile of(String name, String device, String bio, Bitmap avatar,
                                   Bitmap cover, List<RemotePost> posts) {
        return new RemoteProfile(name, device, bio, avatar, cover, posts);
    }

    public static RemoteProfile parse(String body) {
        try {
            JSONObject json = new JSONObject(body);
            List<RemotePost> posts = new ArrayList<>();
            JSONArray array = json.optJSONArray("posts");
            if (array != null) {
                int count = Math.min(array.length(), MAX_POSTS);
                for (int i = 0; i < count; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String text = limit(item.optString("text"), MAX_TEXT);
                    Bitmap image = decode(item.optString("image"));
                    if (text.isEmpty() && image == null) continue;
                    posts.add(new RemotePost(item.optString("id"), text, image,
                            item.optLong("time"), item.optInt("visibility"),
                            Math.max(0, item.optInt("hearts")), item.optBoolean("liked")));
                }
            }
            return new RemoteProfile(
                    json.optString("name"),
                    json.optString("device"),
                    limit(json.optString("bio"), MAX_BIO),
                    decode(json.optString("avatar")),
                    decode(json.optString("cover")),
                    posts);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static Bitmap decode(String data) {
        if (data == null || data.isEmpty() || data.length() > MAX_IMAGE_CHARS) return null;
        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException | OutOfMemoryError e) {
            return null;
        }
    }
}