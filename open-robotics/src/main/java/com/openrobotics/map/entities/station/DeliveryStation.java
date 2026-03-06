package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;

// drop-off location for tasks
public class DeliveryStation extends Station {
    public DeliveryStation(String name, Vector2D position) {
        super(name, position);
    }
}
