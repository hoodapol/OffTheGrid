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

        Address(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final Map<String, Address> PEERS = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private PeerRegistry() {
    }

    public static void put(String name, String host, int port) {
        PEERS.put(name, new Address(host, port));
        notifyChanged();
    }

    public static void putIfAbsent(String name, String host, int port) {
        if (PEERS.putIfAbsent(name, new Address(host, port)) == null) {
            notifyChanged();
        }
    }

    public static void remove(String name) {
        if (PEERS.remove(name) != null) {
            notifyChanged();
        }
    }

    public static Address get(String name) {
        return PEERS.get(name);
    }

    public static boolean contains(String name) {
        return PEERS.containsKey(name);
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

    private static void notifyChanged() {
        for (Listener listener : LISTENERS) {
            listener.onPeersChanged();
        }
    }
}