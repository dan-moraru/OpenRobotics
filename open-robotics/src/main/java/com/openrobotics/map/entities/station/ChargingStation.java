package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;

import java.util.UUID;

/** Charging station; robots travel here to recharge when their battery is low. */
public class ChargingStation extends Station {
    public ChargingStation(String name, Vector2D position) {
        super(name, position);
    }

    public ChargingStation(UUID id, String name, Vector2D position) {
        super(id, name, position);
    }
}
