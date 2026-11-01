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

    private static final String TAG = "Wire";

    private Wire() {
    }

    public static final class Packet {
        public final String type;
        public final String name;
        public final String id;
        public final String text;

        Packet(String type, String name, String id, String text) {
            this.type = type;
            this.name = name;
            this.id = id;
            this.text = text;
        }
    }

    public static String hello(String name) {
        return build(HELLO, name, "", "");
    }

    public static String message(String name, String id, String text) {
        return build(MSG, name, id, text);
    }

    public static String delete(String name, String id) {
        return build(DEL, name, id, "");
    }

    public static String ack() {
        return build(ACK, "", "", "");
    }

    public static Packet parse(String line) {
        if (line == null) return null;
        try {
            JSONObject json = new JSONObject(line);
            String type = json.optString("type");
            String name = json.optString("name");
            String id = json.optString("id");
            String text = json.optString("text");
            if (MSG.equals(type) || DEL.equals(type)) {
                Log.d(TAG, "In: " + line);
            }
            if (ACK.equals(type)) return new Packet(ACK, "", "", "");
            if (HELLO.equals(type) && !name.isEmpty()) return new Packet(HELLO, name, "", "");
            if (MSG.equals(type) && !name.isEmpty() && !id.isEmpty()) {
                return new Packet(MSG, name, id, text);
            }
            if (DEL.equals(type) && !name.isEmpty() && !id.isEmpty()) {
                return new Packet(DEL, name, id, "");
            }
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

    private static String build(String type, String name, String id, String text) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("name", name);
            json.put("id", id);
            json.put("text", text);
            String line = json.toString();
            if (MSG.equals(type) || DEL.equals(type)) {
                Log.d(TAG, "Out: " + line);
            }
            return line;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}