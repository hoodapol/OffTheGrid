package com.example.offthegrid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PendingReactions {

    public static final class Item {
        public final String uid;
        public final boolean on;

        Item(String uid, boolean on) {
            this.uid = uid;
            this.on = on;
        }
    }

    private static final String PREFS = "pending_reactions";
    private static final String KEY = "items";
    private static final String SEP = "\u0001";

    private PendingReactions() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> load(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public static synchronized void put(Context context, String peer, String uid, boolean on) {
        Set<String> items = load(context);
        String prefix = peer + SEP + uid + SEP;
        items.removeIf(entry -> entry.startsWith(prefix));
        items.add(prefix + (on ? "1" : "0"));
        prefs(context).edit().putStringSet(KEY, items).apply();
    }

    public static synchronized void remove(Context context, String peer, String uid, boolean on) {
        Set<String> items = load(context);
        String entry = peer + SEP + uid + SEP + (on ? "1" : "0");
        if (items.remove(entry)) {
            prefs(context).edit().putStringSet(KEY, items).apply();
        }
    }

    public static synchronized List<Item> list(Context context, String peer) {
        List<Item> result = new ArrayList<>();
        String prefix = peer + SEP;
        for (String entry : load(context)) {
            if (!entry.startsWith(prefix)) continue;
            String[] parts = entry.split(SEP, -1);
            if (parts.length != 3) continue;
            result.add(new Item(parts[1], "1".equals(parts[2])));
        }
        return result;
    }
}