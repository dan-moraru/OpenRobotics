package com.openrobotics.map;

import java.util.UUID;

// base class for all physical objects on the map (uml 3.3.4)
public class MapEntity {
    private final UUID id; // unique id for logging in db
    private String name; // user facing label
    private Vector2D position;

    // Constructor for new entities
    public MapEntity(String name, Vector2D position) {
        this(UUID.randomUUID(), name, position);
    }

    // Constructor for loading entities (used primarily by ConfigLoader)
    public MapEntity(UUID id, String name, Vector2D position) {
        this.id = id;
        this.name = name;
        this.position = position;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Vector2D getPosition() { return position; }

    public void setName(String name) { this.name = name; }
    public void setPosition(Vector2D position) { this.position = position; }

    // overridable per-tick update hook
    public void update() {}
}
