package com.example.demo.model;

public class User {
    private String userId;
    private String role;
    private boolean premium;
    private boolean active;

    public User() {}

    public User(String userId, String role, boolean premium, boolean active) {
        this.userId = userId;
        this.role = role;
        this.premium = premium;
        this.active = active;
    }

    // Business logic methods for condition testing
    public boolean isActive() {
        return active;
    }

    public boolean isPremium() {
        return premium;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean getPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
