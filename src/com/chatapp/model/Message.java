package com.chatapp.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private int id;
    private User sender;
    private User receiver; // Dodane pole dla odbiorcy wiadomości prywatnej
    private String content;
    private LocalDateTime timestamp;

    // Konstruktor domyślny dla serializacji
    public Message() {
        this.timestamp = LocalDateTime.now();
    }

    public Message(User sender, String content) {
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // Konstruktor dla wiadomości prywatnych
    public Message(User sender, User receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // Gettery i Settery
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

    // Metoda pomocnicza do sprawdzania, czy wiadomość jest prywatna
    public boolean isPrivate() {
        return receiver != null;
    }

    @Override
    public String toString() {
        if (isPrivate()) {
            return "[" + timestamp.toLocalTime() + "] " + sender.getUsername() + " -> " + receiver.getUsername() + ": " + content;
        } else {
            return "[" + timestamp.toLocalTime() + "] " + sender.getUsername() + ": " + content;
        }
    }
}