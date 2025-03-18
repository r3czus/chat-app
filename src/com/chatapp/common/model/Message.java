package com.chatapp.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {


    private int id;
    private User sender;
    private User receiver;
    private String content;
    private LocalDateTime timestamp;

    public Message() {
        this.timestamp = LocalDateTime.now();
    }

    public Message(User sender, String content) {
        this();
        this.sender = sender;
        this.content = content;
    }

    public Message(User sender, User receiver, String content) {
        this();
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
    }

    // Gettery i settery
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isPrivate() {
        return receiver != null;
    }

    public boolean isSystemMessage() {
        return sender == null;
    }

    @Override
    public String toString() {
        if (isPrivate()) {
            return "[" + timestamp.toLocalTime() + "] " + sender.getUsername() + " -> " + receiver.getUsername() + ": " + content;
        } else {
            return "[" + timestamp.toLocalTime() + "] " + (sender != null ? sender.getUsername() : "System") + ": " + content;
        }
    }
}