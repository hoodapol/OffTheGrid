package com.example.offthegrid;

public class Message {
    private final String text;
    private final boolean sent;

    public Message(String text, boolean sent) {
        this.text = text;
        this.sent = sent;
    }

    public String getText() {
        return text;
    }

    public boolean isSent() {
        return sent;
    }
}