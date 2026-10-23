package com.example.offthegrid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class Profile {

    private static final String PREFS = "profile";
    private static final String KEY_NAME = "name";
    private static final String KEY_ID = "device_id";

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
}