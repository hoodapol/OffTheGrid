package com.example.offthegrid;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TypingTracker {

    public interface Listener {
        void onTypingChanged(String peer);
    }

    private static final long EXPIRE_MS = 5000;
    private static final Map<String, Long> UNTIL = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TypingTracker() {
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static boolean isTyping(String peer) {
        Long until = UNTIL.get(peer);
        return until != null && until > System.currentTimeMillis();
    }

    public static void set(String peer, boolean typing) {
        if (peer == null) return;
        if (typing) {
            UNTIL.put(peer, System.currentTimeMillis() + EXPIRE_MS);
        } else {
            UNTIL.remove(peer);
        }
        MAIN.post(() -> notifyChanged(peer));
        if (typing) {
            MAIN.postDelayed(() -> notifyChanged(peer), EXPIRE_MS + 100);
        }
    }

    private static void notifyChanged(String peer) {
        for (Listener listener : LISTENERS) {
            listener.onTypingChanged(peer);
        }
    }
}