package com.example.offthegrid;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AvatarCache {

    private static final long REFRESH_MS = 5 * 60 * 1000;
    private static final long RETRY_MS = 30 * 1000;

    private static final Map<String, Bitmap> BITMAPS = new ConcurrentHashMap<>();
    private static final Map<String, Long> NEXT_TRY = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AvatarCache() {
    }

    public static Bitmap cached(String peer) {
        return BITMAPS.get(peer);
    }

    public static void request(Context context, String peer, Runnable onChanged) {
        if (!PeerRegistry.contains(peer)) return;
        Long next = NEXT_TRY.get(peer);
        if (next != null && System.currentTimeMillis() < next) return;
        if (!LOADING.add(peer)) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            Bitmap bitmap = PeerClient.fetchAvatar(app, peer);
            MAIN.post(() -> {
                LOADING.remove(peer);
                long wait = bitmap != null ? REFRESH_MS : RETRY_MS;
                NEXT_TRY.put(peer, System.currentTimeMillis() + wait);
                if (bitmap != null) {
                    BITMAPS.put(peer, bitmap);
                    onChanged.run();
                }
            });
        });
    }
}