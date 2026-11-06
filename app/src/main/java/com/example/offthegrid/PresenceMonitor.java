package com.example.offthegrid;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PresenceMonitor {

    private static final String TAG = "PresenceMonitor";
    private static final long INTERVAL_MS = 5000;
    private static final long RESTART_GAP_MS = 45000;
    private static final int MAX_MISSES = 2;
    private static final int MAX_FRIEND_STRIKES = 3;

    private static final Map<String, Integer> MISSES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> STRIKES = new ConcurrentHashMap<>();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();

    private static ScheduledExecutorService scheduler;
    private static ExecutorService pool;
    private static volatile long lastRestart;

    private PresenceMonitor() {
    }

    public static synchronized void start(Context context) {
        if (scheduler != null) return;
        Context app = context.getApplicationContext();
        pool = Executors.newCachedThreadPool();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> sweep(app), INTERVAL_MS, INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        MISSES.clear();
        STRIKES.clear();
        BUSY.clear();
    }

    public static void sweepNow(Context context) {
        Context app = context.getApplicationContext();
        ExecutorService current = pool;
        if (current == null) return;
        try {
            current.execute(() -> sweep(app));
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Sweep rejected", e);
        }
    }

    private static void sweep(Context app) {
        try {
            List<String> names = PeerRegistry.names();
            for (String name : names) {
                if (!BUSY.add(name)) continue;
                ExecutorService current = pool;
                if (current == null) {
                    BUSY.remove(name);
                    return;
                }
                try {
                    current.execute(() -> {
                        try {
                            check(app, name);
                        } finally {
                            BUSY.remove(name);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    BUSY.remove(name);
                }
            }
            FriendSync.syncOnline(app);
            restartIfAlone(app, names);
        } catch (RuntimeException e) {
            Log.w(TAG, "Sweep failed", e);
        }
    }

    private static void check(Context app, String name) {
        PeerRegistry.Address address = PeerRegistry.get(name);
        if (address == null) {
            MISSES.remove(name);
            return;
        }
        Wire.Packet reply = PeerClient.identity(app, name);
        if (reply != null) {
            MISSES.remove(name);
            reconcileFriend(app, address.deviceId, reply.text);
            return;
        }
        Integer previous = MISSES.get(name);
        int misses = previous == null ? 1 : previous + 1;
        if (misses >= MAX_MISSES) {
            MISSES.remove(name);
            if (PeerRegistry.get(name) == address) {
                PeerRegistry.remove(name);
            }
        } else {
            MISSES.put(name, misses);
        }
    }

    private static void reconcileFriend(Context app, String device, String flag) {
        if (!"0".equals(flag)) {
            STRIKES.remove(device);
            return;
        }
        FriendStore.Friend friend = FriendStore.get(app, device);
        if (friend == null || friend.state != FriendStore.STATE_FRIEND) {
            STRIKES.remove(device);
            return;
        }
        Integer previous = STRIKES.get(device);
        int strikes = previous == null ? 1 : previous + 1;
        if (strikes >= MAX_FRIEND_STRIKES) {
            STRIKES.remove(device);
            FriendStore.remove(app, device);
        } else {
            STRIKES.put(device, strikes);
        }
    }

    private static void restartIfAlone(Context app, List<String> names) {
        if (!names.isEmpty() || !onWifi(app)) return;
        long now = System.currentTimeMillis();
        if (now - lastRestart < RESTART_GAP_MS) return;
        lastRestart = now;
        ListenerService.restartDiscovery(app);
    }

    private static boolean onWifi(Context app) {
        ConnectivityManager manager =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}