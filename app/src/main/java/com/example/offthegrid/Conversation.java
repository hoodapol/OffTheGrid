package com.example.offthegrid;

public final class Conversation {

    public final String peer;
    public final String lastText;
    public final long lastTimestamp;
    public final boolean lastFromMe;

    public Conversation(String peer, String lastText, long lastTimestamp, boolean lastFromMe) {
        this.peer = peer;
        this.lastText = lastText;
        this.lastTimestamp = lastTimestamp;
        this.lastFromMe = lastFromMe;
    }
}