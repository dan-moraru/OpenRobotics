package com.openrobotics.task;

import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;

// runtime task assigned to a robot (warehouse entities subsystem)
public class Task {
    private final int id; // unique id
    private final Vector2D pickupLocation;
    private final Vector2D dropoffLocation;
    private int priority; // higher = more urgent
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED

    public Task(int id, Vector2D pickupLocation, Vector2D dropoffLocation, int priority) {
        this.id = id;
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        this.priority = priority;
        this.status = "PENDING";
    }

    public int getId() { return id; }
    public Vector2D getPickupLocation() { return pickupLocation; }
    public Vector2D getDropoffLocation() { return dropoffLocation; }
    public int getPriority() { return priority; }
    public String getStatus() { return status; }

    public void setPriority(int priority) { this.priority = priority; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "Task{id=" + id + ", status=" + status + ", priority=" + priority + "}";
    }

    public Vector2D getPickup() {
        return pickupLocation;
    }

    public Vector2D getDropoff() {
        return dropoffLocation;
    }
}
