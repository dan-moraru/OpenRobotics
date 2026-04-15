package com.openrobotics.map;

import java.util.UUID;

/** Base class for all physical objects placed on the warehouse map. */
public class MapEntity {
    private final UUID id; // unique id for logging in db
    private String name; // user-facing label
    private Vector2D position;

    /**
     * Creates a new entity with a randomly generated ID.
     *
     * @param name the user-facing label for this entity
     * @param position the initial grid position
     */
    public MapEntity(String name, Vector2D position) {
        this(UUID.randomUUID(), name, position);
    }

    /**
     * Creates an entity with an explicit ID; used when loading from the database.
     *
     * @param id the entity's unique identifier
     * @param name the user-facing label for this entity
     * @param position the initial grid position
     */
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

    /**
     * Per-tick update hook; Robot overrides this to execute state-dependent behaviour each tick.
     *
     * @param map the current warehouse map, available to subclasses for spatial queries
     */
    public void update(Map map) {}
}
