package com.example.offthegrid;

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
    public static final String ACK = "ACK";

    private static final String SEPARATOR = "\t";

    private Wire() {
    }

    public static final class Packet {
        public final String type;
        public final String name;
        public final String text;

        Packet(String type, String name, String text) {
            this.type = type;
            this.name = name;
            this.text = text;
        }
    }

    public static String hello(String name) {
        return HELLO + SEPARATOR + clean(name);
    }

    public static String message(String name, String text) {
        return MSG + SEPARATOR + clean(name) + SEPARATOR + clean(text);
    }

    public static String ack() {
        return ACK;
    }

    public static Packet parse(String line) {
        if (line == null) return null;
        String[] parts = line.split(SEPARATOR, 3);
        String type = parts[0];
        if (ACK.equals(type)) return new Packet(ACK, "", "");
        if (HELLO.equals(type) && parts.length >= 2) return new Packet(HELLO, parts[1], "");
        if (MSG.equals(type) && parts.length == 3) return new Packet(MSG, parts[1], parts[2]);
        return null;
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

    private static String clean(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}