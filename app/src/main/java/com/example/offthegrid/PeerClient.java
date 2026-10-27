package com.example.offthegrid;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class PeerClient {

    private static final String TAG = "PeerClient";
    private static final int TIMEOUT_MS = 3000;

    private PeerClient() {
    }

    public static String hello(Context context, String host, int port) {
        try (Socket socket = open(host, port);
             BufferedWriter out = Wire.writer(socket);
             BufferedReader in = Wire.reader(socket)) {
            Wire.writeLine(out, Wire.hello(Profile.getName(context)));
            Wire.Packet reply = Wire.parse(in.readLine());
            if (reply != null && Wire.HELLO.equals(reply.type)) {
                return reply.name;
            }
        } catch (IOException e) {
            Log.w(TAG, "Hello to " + host + ":" + port + " failed", e);
        }
        return null;
    }

    public static boolean sendMessage(Context context, String peer, String text) {
        PeerRegistry.Address address = PeerRegistry.get(peer);
        if (address == null) return false;
        try (Socket socket = open(address.host, address.port);
             BufferedWriter out = Wire.writer(socket);
             BufferedReader in = Wire.reader(socket)) {
            Wire.writeLine(out, Wire.message(Profile.getName(context), text));
            Wire.Packet reply = Wire.parse(in.readLine());
            return reply != null && Wire.ACK.equals(reply.type);
        } catch (IOException e) {
            Log.w(TAG, "Send to " + peer + " failed", e);
            return false;
        }
    }

    private static Socket open(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        return socket;
    }
}