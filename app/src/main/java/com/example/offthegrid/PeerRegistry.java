package com.example.offthegrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PeerRegistry {

    public interface Listener {
        void onPeersChanged();
    }

    public static final class Address {
        public final String host;
        public final int port;
        public final String deviceId;

        Address(String host, int port, String deviceId) {
            this.host = host;
            this.port = port;
            this.deviceId = deviceId;
        }
    }

    private static final Map<String, Address> PEERS = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private PeerRegistry() {
    }

    public static synchronized String register(String name, String deviceId, String host, int port) {
        String key = keyFor(deviceId);
        if (key == null) {
            key = freeKey(name, deviceId);
        } else {
            Address current = PEERS.get(key);
            if (current != null && current.host.equals(host) && current.port == port) {
                return key;
            }
        }
        PEERS.put(key, new Address(host, port, deviceId));
        notifyChanged();
        return key;
    }

    public static synchronized String registerIfAbsent(String name, String deviceId, String host,
                                                       int port) {
        String key = keyFor(deviceId);
        if (key != null) return key;
        return register(name, deviceId, host, port);
    }

    public static synchronized void remove(String key) {
        if (PEERS.remove(key) != null) {
            notifyChanged();
        }
    }

    public static void touch() {
        notifyChanged();
    }

    public static Address get(String key) {
        return PEERS.get(key);
    }

    public static boolean contains(String key) {
        return PEERS.containsKey(key);
    }

    public static String keyFor(String deviceId) {
        for (Map.Entry<String, Address> entry : PEERS.entrySet()) {
            if (entry.getValue().deviceId.equals(deviceId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>(PEERS.keySet());
        Collections.sort(names);
        return names;
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static String freeKey(String name, String deviceId) {
        if (!PEERS.containsKey(name)) return name;
        for (int length = 4; length <= deviceId.length(); length += 2) {
            String key = name + " (" + deviceId.substring(0, length) + ")";
            if (!PEERS.containsKey(key)) return key;
        }
        return name + " (" + deviceId + ")";
    }

    private static void notifyChanged() {
        for (Listener listener : LISTENERS) {
            listener.onPeersChanged();
        }
    }
}