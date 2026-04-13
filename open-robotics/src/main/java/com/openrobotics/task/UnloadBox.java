package com.openrobotics.task;

import com.openrobotics.map.Vector2D;

/** specific task type for unloading a box (uml 3.3.4) */
public class UnloadBox extends Task {
    public UnloadBox(int id, Vector2D pickupLocation, Vector2D dropoffLocation, int priority) {
        super(id, pickupLocation, dropoffLocation, priority);
    }
}
