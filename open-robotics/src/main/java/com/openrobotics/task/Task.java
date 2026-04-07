package com.openrobotics.task;

import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;

import java.util.Objects;

/**
 * Represents tasks that will be assigned to robots during the simulation of a warehouses workload
 */
public class Task implements Comparable<Task> {
    private final int id; // unique id
    private final Vector2D pickupLocation;
    private final Vector2D dropoffLocation;
    private int priority; // higher = more urgent
    private TaskStatus status; // PENDING, IN_PROGRESS, COMPLETED, FAILED

    public Task(int id, Vector2D pickupLocation, Vector2D dropoffLocation, int priority) {
        this.id = id;
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        this.priority = priority;
        this.status = TaskStatus.PENDING;
    }

    // Getters
    public int getId() { return id; }
    public Vector2D getPickupLocation() { return pickupLocation; }
    public Vector2D getDropoffLocation() { return dropoffLocation; }
    public int getPriority() { return priority; }
    public TaskStatus getStatus() { return status; }

    // Setters
    /**
     * Avoid mutating priority while this task is stored in sorted collections,
     * as it can invalidate ordering assumptions.
     */
    @Deprecated
    public void setPriority(int priority) { this.priority = priority; }
    public void setStatus(TaskStatus status) { this.status = status; }

    @Override
    public String toString() {
        return "Task{id=" + id + ", status=" + status + ", priority=" + priority + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Task) {
            Task task = (Task) obj;
            return this.id == task.id;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(Task task) {
        int byPriority = Integer.compare(task.priority, this.priority);
        if (byPriority != 0) {
            return byPriority;
        }
        return Integer.compare(this.id, task.id);
    }
}
