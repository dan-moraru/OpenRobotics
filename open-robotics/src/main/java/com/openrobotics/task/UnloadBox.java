package com.openrobotics.task;

import com.openrobotics.map.Vector2D;

/** Specific task type for unloading a box. */
public class UnloadBox extends Task {
    public UnloadBox(int id, Vector2D pickupLocation, Vector2D dropoffLocation, int priority) {
        super(id, pickupLocation, dropoffLocation, priority);
    }
}
