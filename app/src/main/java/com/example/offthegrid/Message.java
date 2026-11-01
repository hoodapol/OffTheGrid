package com.example.offthegrid;

public class Message {
    private final long id;
    private final String uid;
    private final String text;
    private final boolean sent;
    private final int status;

    public Message(long id, String uid, String text, boolean sent, int status) {
        this.id = id;
        this.uid = uid;
        this.text = text;
        this.sent = sent;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public String getText() {
        return text;
    }

    public boolean isSent() {
        return sent;
    }

    public boolean isPending() {
        return sent && uid != null && status == MessageStorage.STATUS_PENDING;
    }
}