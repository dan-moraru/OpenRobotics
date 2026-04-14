package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;

import java.util.UUID;

/** delivery station; robots drop off boxes here to complete a task */
public class DeliveryStation extends Station {
    public DeliveryStation(String name, Vector2D position) {
        super(name, position);
    }

    public DeliveryStation(UUID id, String name, Vector2D position) {
        super(id, name, position);
    }
}
