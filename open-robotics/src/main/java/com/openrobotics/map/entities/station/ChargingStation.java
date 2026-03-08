package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;

import java.util.UUID;

// robot charging location
public class ChargingStation extends Station {
    public ChargingStation(String name, Vector2D position) {
        super(name, position);
    }

    // constructor for loading charging stations from config
    public ChargingStation(UUID id, String name, Vector2D position) {
        super(id, name, position);
    }
}
