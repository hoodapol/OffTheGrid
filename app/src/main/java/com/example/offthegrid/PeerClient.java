package com.example.offthegrid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class PeerClient {

    private static final String TAG = "PeerClient";
    private static final int TIMEOUT_MS = 3000;
    private static final int IMAGE_TIMEOUT_MS = 15000;
    private static final int PROFILE_TIMEOUT_MS = 20000;

    private PeerClient() {
    }

    public static Wire.Packet hello(Context context, String host, int port) {
        try {
            return requestIdentity(context, host, port);
        } catch (IOException e) {
            Log.w(TAG, "Hello to " + host + ":" + port + " failed", e);
            return null;
        }
    }

    public static boolean ping(Context context, String peer) {
        PeerRegistry.Address address = PeerRegistry.get(peer);
        if (address == null) return false;
        try {
            Wire.Packet reply = requestIdentity(context, address.host, address.port);
            return reply != null && address.deviceId.equals(reply.device);
        } catch (IOException e) {
            return false;
        }
    }

    private static Wire.Packet requestIdentity(Context context, String host, int port)
            throws IOException {
        try (Socket socket = open(host, port, TIMEOUT_MS);
             BufferedWriter out = Wire.writer(socket);
             BufferedReader in = Wire.reader(socket)) {
            Wire.writeLine(out, Wire.hello(Profile.getName(context), Profile.getId(context)));
            Wire.Packet reply = Wire.parse(in.readLine());
            if (reply != null && Wire.HELLO.equals(reply.type)) {
                return reply;
            }
            return null;
        }
    }

    public static boolean sendMessage(Context context, String peer, String uid, String text) {
        return sendAndAwaitAck(peer, Wire.message(
                Profile.getName(context), Profile.getId(context), uid, text, ""), TIMEOUT_MS);
    }

    public static boolean sendImage(Context context, String peer, String uid, String image) {
        String data = ImageStore.readBase64(context, image);
        if (data == null) return false;
        return sendAndAwaitAck(peer, Wire.message(
                Profile.getName(context), Profile.getId(context), uid, "", data), IMAGE_TIMEOUT_MS);
    }

    public static boolean sendDelete(Context context, String peer, String uid) {
        return sendAndAwaitAck(peer, Wire.delete(
                Profile.getName(context), Profile.getId(context), uid), TIMEOUT_MS);
    }

    public static boolean sendFriend(Context context, String peer, String action) {
        return sendAndAwaitAck(peer, Wire.friend(
                Profile.getName(context), Profile.getId(context), action), TIMEOUT_MS);
    }

    public static Bitmap fetchAvatar(Context context, String peer) {
        PeerRegistry.Address address = PeerRegistry.get(peer);
        if (address == null) return null;
        try (Socket socket = open(address.host, address.port, TIMEOUT_MS);
             BufferedWriter out = Wire.writer(socket);
             BufferedReader in = Wire.reader(socket)) {
            Wire.writeLine(out, Wire.getAvatar(Profile.getName(context), Profile.getId(context)));
            Wire.Packet reply = Wire.parse(in.readLine());
            if (reply == null || !Wire.AVATAR.equals(reply.type)) return null;
            if (!address.deviceId.equals(reply.device) || reply.image.isEmpty()) return null;
            byte[] bytes = Base64.decode(reply.image, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IOException | IllegalArgumentException e) {
            Log.w(TAG, "Avatar fetch from " + peer + " failed", e);
            return null;
        }
    }

    public static RemoteProfile fetchProfile(Context context, String peer) {
        PeerRegistry.Address address = PeerRegistry.get(peer);
        if (address == null) return null;
        try (Socket socket = open(address.host, address.port, PROFILE_TIMEOUT_MS);
             BufferedWriter out = Wire.writer(socket);
             BufferedReader in = Wire.reader(socket)) {
            Wire.writeLine(out, Wire.getProfile(Profile.getName(context), Profile.getId(context)));
            Wire.Packet reply = Wire.parse(in.readLine());
            if (reply == null || !Wire.PROFILE.equals(reply.type)) return null;
            if (!address.deviceId.equals(reply.device)) return null;
            return RemoteProfile.parse(reply.body);
        } catch (IOException e) {
            Log.w(TAG, "Profile fetch from " + peer + " failed", e);
            return null;
        }
    }

    private static boolean sendAndAwaitAck(String peer, String line, int readTimeoutMs) {
        PeerRegistry.Address address = PeerRegistry.get(peer);
        if (address == null) return false;
        try (Socket socket = open(address.host, address.port, readTimeoutMs);
             BufferedWriter out = Wire.writer(socket);
             BufferedReader in = Wire.reader(socket)) {
            Wire.writeLine(out, line);
            Wire.Packet reply = Wire.parse(in.readLine());
            return reply != null && Wire.ACK.equals(reply.type);
        } catch (IOException e) {
            Log.w(TAG, "Send to " + peer + " failed", e);
            return false;
        }
    }

    private static Socket open(String host, int port, int readTimeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(readTimeoutMs);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        return socket;
    }
}