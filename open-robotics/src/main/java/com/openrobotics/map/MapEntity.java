package com.openrobotics.map;

import java.util.UUID;

/** base class for all physical objects placed on the warehouse map (uml 3.3.4) */
public class MapEntity {
    private final UUID id; // unique id for logging in db
    private String name; // user facing label
    private Vector2D position;

    public MapEntity(String name, Vector2D position) {
        this(UUID.randomUUID(), name, position);
    }

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

    /** per-tick update hook; Robot overrides this to execute state-dependent behavior each tick */
    public void update(Map map) {}
}
