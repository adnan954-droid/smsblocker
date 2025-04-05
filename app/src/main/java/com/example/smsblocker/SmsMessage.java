package com.example.smsblocker;

public class SmsMessage {
    private String senderName;
    private String senderPhoneNumber;
    private String body;
    private String formattedDate;
    private long timestamp;
    private int readState;
    private long messageId;
    private boolean isBlocked;

    // Constructor
    public SmsMessage(String senderName, String senderPhoneNumber, String body, long timestamp, int readState, long messageId, boolean isBlocked) {
        this.senderName = senderName;
        this.senderPhoneNumber = senderPhoneNumber;
        this.body = body;
        this.formattedDate = this.formattedDate;
        this.timestamp = timestamp;
        this.readState = readState;
        this.messageId = messageId;
        this.isBlocked = isBlocked;
    }

    // ✅ Add the setter method for isBlocked
    public void setBlocked(boolean blocked) {
        this.isBlocked = blocked;
    }

    // ✅ Add these getter methods if missing
    public String getBody() {
        return body;
    }

    public int getReadState() {
        return readState;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderPhoneNumber() {
        return senderPhoneNumber;
    }

    public String getFormattedDate() {
        return formattedDate;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getMessageId() {
        return messageId;
    }

    public boolean isBlocked() {
        return isBlocked;
    }
}
