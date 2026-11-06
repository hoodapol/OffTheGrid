package com.example.offthegrid;

public class Message {
    private final long id;
    private final String uid;
    private final String text;
    private final boolean sent;
    private final int status;
    private final String image;
    private final String replyQuote;
    private final boolean heartedByMe;
    private final boolean heartedByThem;
    private final long timestamp;

    public Message(long id, String uid, String text, boolean sent, int status, String image) {
        this(id, uid, text, sent, status, image, null, false, false, 0);
    }

    public Message(long id, String uid, String text, boolean sent, int status, String image,
                   String replyQuote) {
        this(id, uid, text, sent, status, image, replyQuote, false, false, 0);
    }

    public Message(long id, String uid, String text, boolean sent, int status, String image,
                   String replyQuote, boolean heartedByMe, boolean heartedByThem) {
        this(id, uid, text, sent, status, image, replyQuote, heartedByMe, heartedByThem, 0);
    }

    public Message(long id, String uid, String text, boolean sent, int status, String image,
                   String replyQuote, boolean heartedByMe, boolean heartedByThem,
                   long timestamp) {
        this.id = id;
        this.uid = uid;
        this.text = text;
        this.sent = sent;
        this.status = status;
        this.image = image;
        this.replyQuote = replyQuote;
        this.heartedByMe = heartedByMe;
        this.heartedByThem = heartedByThem;
        this.timestamp = timestamp;
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

    public String getImage() {
        return image;
    }

    public boolean hasImage() {
        return image != null;
    }

    public String getReplyQuote() {
        return replyQuote;
    }

    public boolean hasReply() {
        return replyQuote != null && !replyQuote.isEmpty();
    }

    public boolean isHeartedByMe() {
        return heartedByMe;
    }

    public boolean isHeartedByThem() {
        return heartedByThem;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean canHeart() {
        return uid != null;
    }

    public boolean isPending() {
        return sent && uid != null && status == MessageStorage.STATUS_PENDING;
    }
}