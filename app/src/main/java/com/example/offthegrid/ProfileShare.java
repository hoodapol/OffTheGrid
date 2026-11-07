package com.example.offthegrid;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ProfileShare {

    private static final int MAX_POSTS = 10;
    private static final int AVATAR_SIDE = 256;
    private static final int AVATAR_BYTES = 40 * 1024;
    private static final int COVER_SIDE = 640;
    private static final int COVER_BYTES = 80 * 1024;
    private static final int POST_SIDE = 480;
    private static final int POST_BYTES = 60 * 1024;

    private ProfileShare() {
    }

    public static String build(Context context, boolean includeFriendsOnly) {
        return build(context, includeFriendsOnly, null);
    }

    public static String build(Context context, boolean includeFriendsOnly, String requester) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", Wire.PROFILE);
            json.put("name", Profile.getName(context));
            json.put("device", Profile.getId(context));
            json.put("bio", Profile.getBio(context));
            json.put("avatar", ImageStore.readScaledBase64(
                    context, Profile.getAvatar(context), AVATAR_SIDE, AVATAR_BYTES));
            json.put("cover", ImageStore.readScaledBase64(
                    context, Profile.getCover(context), COVER_SIDE, COVER_BYTES));
            JSONArray posts = new JSONArray();
            for (Post post : PostStore.loadMine(context)) {
                if (posts.length() >= MAX_POSTS) break;
                if (post.visibility == PostStorage.VISIBILITY_FRIENDS && !includeFriendsOnly) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("id", post.uid);
                item.put("text", post.text);
                item.put("time", post.timestamp);
                item.put("visibility", post.visibility);
                item.put("edited", PostStore.isEdited(context, post.uid));
                item.put("hearts", MessageMetaStore.heartCount(context, post.uid));
                item.put("liked", requester != null
                        && MessageMetaStore.isHeartedBy(context, post.uid, requester));
                item.put("image", post.hasImage()
                        ? ImageStore.readScaledBase64(context, post.image, POST_SIDE, POST_BYTES)
                        : "");
                posts.put(item);
            }
            json.put("posts", posts);
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}