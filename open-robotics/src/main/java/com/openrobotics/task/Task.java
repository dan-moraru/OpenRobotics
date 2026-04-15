package com.openrobotics.task;

import com.openrobotics.map.Vector2D;

import java.util.Objects;

/** A task assigned to a robot; pairs a rack pickup location with a delivery station dropoff. */
public class Task implements Comparable<Task> {
    private final long id; // unique id
    private long artificialId; // temporary fix for disconnect between database and application task ids
    private Vector2D pickupLocation;
    private Vector2D dropoffLocation;
    private int priority; // higher = more urgent
    private TaskStatus status; // PENDING, IN_PROGRESS, COMPLETED, FAILED

    /**
     * Creates a new task with the given id, locations, and priority.
     * Status is initialised to {@link TaskStatus#PENDING}.
     *
     * @param id unique task identifier
     * @param pickupLocation the rack tile the robot must visit first
     * @param dropoffLocation the delivery station tile the robot must deliver to
     * @param priority higher values are more urgent
     */
    public Task(long id, Vector2D pickupLocation, Vector2D dropoffLocation, int priority) {
        this.id = id;
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        this.priority = priority;
        this.status = TaskStatus.PENDING;
    }

    public long getId() { return id; }
    public long getArtificialId() { return artificialId; }
    public Vector2D getPickupLocation() { return pickupLocation; }
    public Vector2D getDropoffLocation() { return dropoffLocation; }
    public int getPriority() { return priority; }
    public TaskStatus getStatus() { return status; }

    /**
     * Mutates the task priority; avoid calling while this task is in a sorted collection
     * as it can invalidate ordering assumptions.
     *
     * @param priority the new priority value
     */
    @Deprecated
    public void setPriority(int priority) { this.priority = priority; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setArtificialId(long artificialId) { this.artificialId = artificialId; }
    public void setPickupLocation(Vector2D pickupLocation) { this.pickupLocation = pickupLocation; }
    public void setDropoffLocation(Vector2D dropoffLocation) { this.dropoffLocation = dropoffLocation; }

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

    // higher priority first; ties broken by ascending task id
    @Override
    public int compareTo(Task task) {
        int byPriority = Integer.compare(task.priority, this.priority);
        if (byPriority != 0) {
            return byPriority;
        }
        return Long.compare(this.id, task.id);
    }
}
