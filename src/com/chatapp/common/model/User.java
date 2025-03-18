package com.chatapp.common.model;

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

    // Gettery i settery
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return id == user.id && username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return 31 * id + (username != null ? username.hashCode() : 0);
    }
}