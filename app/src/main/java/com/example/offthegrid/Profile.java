package com.example.offthegrid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class Profile {

    private static final String PREFS = "profile";
    private static final String KEY_NAME = "name";
    private static final String KEY_ID = "device_id";
    private static final String KEY_BIO = "bio";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_COVER = "cover";

    private Profile() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isSetUp(Context context) {
        return !getName(context).isEmpty();
    }

    public static String getName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static String getBio(Context context) {
        return prefs(context).getString(KEY_BIO, "");
    }

    public static String getAvatar(Context context) {
        return prefs(context).getString(KEY_AVATAR, null);
    }

    public static String getCover(Context context) {
        return prefs(context).getString(KEY_COVER, null);
    }

    public static String getId(Context context) {
        SharedPreferences preferences = prefs(context);
        String id = preferences.getString(KEY_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            preferences.edit().putString(KEY_ID, id).apply();
        }
        return id;
    }

    public static void save(Context context, String name) {
        getId(context);
        prefs(context).edit().putString(KEY_NAME, name).apply();
    }

    public static void saveDetails(Context context, String name, String bio, String avatar,
                                   String cover) {
        getId(context);
        prefs(context).edit()
                .putString(KEY_NAME, name)
                .putString(KEY_BIO, bio)
                .putString(KEY_AVATAR, avatar)
                .putString(KEY_COVER, cover)
                .apply();
    }
}