package com.example.offthegrid;

import android.content.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FriendSync {

    private static final long MIN_GAP_MS = 60 * 1000;
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private FriendSync() {
    }

    public static void syncOnline(Context context) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        for (FriendStore.Friend friend : FriendStore.list(app, FriendStore.STATE_FRIEND)) {
            String key = PeerRegistry.keyFor(friend.device);
            if (key == null) continue;
            Long last = LAST.get(friend.device);
            if (last != null && now - last < MIN_GAP_MS) continue;
            LAST.put(friend.device, now);
            WORKER.execute(() -> {
                RemoteProfile profile = PeerClient.fetchProfile(app, key);
                if (profile != null) {
                    FriendStore.saveCache(app, friend.device, profile);
                }
            });
        }
    }
}