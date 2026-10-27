package com.example.offthegrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PeerRegistry {

    public static final class Address {
        public final String host;
        public final int port;

        Address(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final Map<String, Address> PEERS = new ConcurrentHashMap<>();

    private PeerRegistry() {
    }

    public static void put(String name, String host, int port) {
        PEERS.put(name, new Address(host, port));
    }

    public static Address get(String name) {
        return PEERS.get(name);
    }

    public static boolean contains(String name) {
        return PEERS.containsKey(name);
    }

    public static List<String> names() {
        return new ArrayList<>(PEERS.keySet());
    }
}