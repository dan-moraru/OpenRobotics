package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.MapEntity;

import java.util.UUID;

// storage rack entity (uml 3.3.4)
public class Rack extends MapEntity {
    public Rack(String name, Vector2D position) {
        super(name, position);
    }

    // Constructor for loading racks
    public Rack(UUID id, String name, Vector2D position) {
        super(id, name, position);
    }

    // take a box from this rack (stub for future implementation)
    public void take(Object box) {
        // will be implemented when task/box handling is fleshed out
    }
}
