package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.MapEntity;

import java.util.UUID;

/** impassable map entity; stops sensor rays and blocks traversal (uml 3.3.4) */
public class Obstacle extends MapEntity {
    public Obstacle(String name, Vector2D position) {
        super(name, position);
    }

    public Obstacle(UUID id, String name, Vector2D position) {
        super(id, name, position);
    }
}
