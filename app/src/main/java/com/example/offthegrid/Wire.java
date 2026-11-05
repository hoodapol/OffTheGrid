package com.example.offthegrid;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class Wire {

    public static final String HELLO = "HELLO";
    public static final String MSG = "MSG";
    public static final String DEL = "DEL";
    public static final String ACK = "ACK";
    public static final String GET_PROFILE = "GET_PROFILE";
    public static final String PROFILE = "PROFILE";
    public static final String GET_AVATAR = "GET_AVATAR";
    public static final String AVATAR = "AVATAR";
    public static final String FRIEND = "FRIEND";

    private static final String TAG = "Wire";
    private static final int LOG_LIMIT = 300;
    private static final int MAX_LINE = 6 * 1024 * 1024;

    private Wire() {
    }

    public static final class Packet {
        public final String type;
        public final String name;
        public final String device;
        public final String id;
        public final String text;
        public final String image;
        public final String body;

        Packet(String type, String name, String device, String id, String text, String image,
               String body) {
            this.type = type;
            this.name = name;
            this.device = device;
            this.id = id;
            this.text = text;
            this.image = image;
            this.body = body;
        }
    }

    public static String hello(String name, String device) {
        return build(HELLO, name, device, "", "", "");
    }

    public static String getProfile(String name, String device) {
        return build(GET_PROFILE, name, device, "", "", "");
    }

    public static String getAvatar(String name, String device) {
        return build(GET_AVATAR, name, device, "", "", "");
    }

    public static String avatar(String name, String device, String image) {
        return build(AVATAR, name, device, "", "", image);
    }

    public static String friend(String name, String device, String action) {
        return build(FRIEND, name, device, "", action, "");
    }

    public static String message(String name, String device, String id, String text, String image) {
        return build(MSG, name, device, id, text, image);
    }

    public static String delete(String name, String device, String id) {
        return build(DEL, name, device, id, "", "");
    }

    public static String ack() {
        return build(ACK, "", "", "", "", "");
    }

    public static Packet parse(String line) {
        if (line == null || line.length() > MAX_LINE) return null;
        try {
            JSONObject json = new JSONObject(line);
            String type = json.optString("type");
            String name = json.optString("name");
            String device = json.optString("device");
            String id = json.optString("id");
            String text = json.optString("text");
            String image = json.optString("image");
            if (MSG.equals(type) || DEL.equals(type)) {
                Log.d(TAG, "In: " + brief(line));
            }
            if (ACK.equals(type)) return new Packet(ACK, "", "", "", "", "", "");
            if (name.isEmpty() || device.isEmpty()) return null;
            if (HELLO.equals(type)) return new Packet(HELLO, name, device, "", "", "", "");
            if (GET_PROFILE.equals(type)) {
                return new Packet(GET_PROFILE, name, device, "", "", "", "");
            }
            if (PROFILE.equals(type)) return new Packet(PROFILE, name, device, "", "", "", line);
            if (GET_AVATAR.equals(type)) {
                return new Packet(GET_AVATAR, name, device, "", "", "", "");
            }
            if (AVATAR.equals(type)) return new Packet(AVATAR, name, device, "", "", image, "");
            if (FRIEND.equals(type)) return new Packet(FRIEND, name, device, "", text, "", "");
            if (id.isEmpty()) return null;
            if (MSG.equals(type)) return new Packet(MSG, name, device, id, text, image, "");
            if (DEL.equals(type)) return new Packet(DEL, name, device, id, "", "", "");
            return null;
        } catch (JSONException e) {
            return null;
        }
    }

    public static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    public static BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public static void writeLine(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write('\n');
        out.flush();
    }

    private static String build(String type, String name, String device, String id, String text,
                                String image) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("name", name);
            json.put("device", device);
            json.put("id", id);
            json.put("text", text);
            json.put("image", image);
            String line = json.toString();
            if (MSG.equals(type) || DEL.equals(type)) {
                Log.d(TAG, "Out: " + brief(line));
            }
            return line;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String brief(String line) {
        if (line.length() <= LOG_LIMIT) return line;
        return line.substring(0, LOG_LIMIT) + "... (" + line.length() + " chars)";
    }
}