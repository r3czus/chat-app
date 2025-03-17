package com.chatapp.model;

import java.io.Serializable;

public class User implements Serializable {
    private int id;
    private String username;
    private String password;

    // Konstruktor domyślny dla serializacji
    public User() {
    }

    public User(int id, String username) {
        this.id = id;
        this.username = username;
    }

    // Gettery i Settery
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return username;
    }
}