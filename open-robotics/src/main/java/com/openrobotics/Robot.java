package com.openrobotics;

public class Robot {
    private String id;
    private double x, y; // Position in the warehouse

    public Robot(String id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    // Getters
    public String getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String getStatus() {
        return "Idle";
    }
}