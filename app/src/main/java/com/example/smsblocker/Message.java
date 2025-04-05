package com.example.smsblocker;

public class Message {
    private final String body;
    private final String timestamp;
    private final boolean isReceived;

    // Constructor with three parameters
    public Message(String body, String timestamp, boolean isReceived) {
        this.body = body;
        this.timestamp = timestamp != null ? timestamp : "Unknown"; // Default to "Unknown" if timestamp is null
        this.isReceived = isReceived;
    }

    public String getBody() {
        return body;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isReceived() {
        return isReceived;
    }
}
