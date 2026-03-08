package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;

import java.util.UUID;

// drop-off location for tasks
public class DeliveryStation extends Station {
    public DeliveryStation(String name, Vector2D position) {
        super(name, position);
    }

    // constructor for loading delivery stations from config
    public DeliveryStation(UUID id, String name, Vector2D position) {
        super(id, name, position);
    }
}
