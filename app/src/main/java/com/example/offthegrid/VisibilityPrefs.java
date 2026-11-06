package com.example.offthegrid;

import android.content.Context;
import android.content.SharedPreferences;

public final class VisibilityPrefs {

    private static final String PREFS = "visibility_prefs";
    private static final String KEY = "stay_visible";

    private VisibilityPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isStayVisible(Context context) {
        return prefs(context).getBoolean(KEY, true);
    }

    public static void setStayVisible(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY, value).apply();
    }
}