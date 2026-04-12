package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;

import java.util.UUID;

/** charging station; robots travel here to recharge when battery is low */
public class ChargingStation extends Station {
    public ChargingStation(String name, Vector2D position) {
        super(name, position);
    }

    public ChargingStation(UUID id, String name, Vector2D position) {
        super(id, name, position);
    }
}
