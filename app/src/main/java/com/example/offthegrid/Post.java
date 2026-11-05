package com.example.offthegrid;

public final class Post {

    public final long id;
    public final String uid;
    public final String owner;
    public final String text;
    public final String image;
    public final long timestamp;
    public final int visibility;

    public Post(long id, String uid, String owner, String text, String image, long timestamp,
                int visibility) {
        this.id = id;
        this.uid = uid;
        this.owner = owner;
        this.text = text == null ? "" : text;
        this.image = image;
        this.timestamp = timestamp;
        this.visibility = visibility;
    }

    public boolean hasImage() {
        return image != null && !image.isEmpty();
    }
}