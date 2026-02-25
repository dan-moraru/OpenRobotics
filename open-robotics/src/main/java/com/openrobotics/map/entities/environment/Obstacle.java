package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.MapEntity;

// impassable map entity (uml 3.3.4)
public class Obstacle extends MapEntity {
    public Obstacle(String name, Vector2D position) {
        super(name, position);
    }
}
