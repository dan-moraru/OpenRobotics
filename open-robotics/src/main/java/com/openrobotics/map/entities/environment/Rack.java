package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.MapEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Storage rack; robots pick up boxes from an adjacent tile, never from the rack tile itself. */
public class Rack extends MapEntity {
    private int boxCount;
    private List<UUID> validDropoffIds;
    private boolean manualDropoffAssignment = false;

    /**
     * Creates a new rack with a randomly generated ID and an initial box count of 1.
     *
     * @param name the user-facing label
     * @param position the initial grid position
     */
    public Rack(String name, Vector2D position) {
        super(name, position);
        this.boxCount = 1;
        this.validDropoffIds = new ArrayList<>();
    }

    /**
     * Creates a rack with an explicit ID; used when loading from the database.
     *
     * @param id the entity's unique identifier
     * @param name the user-facing label
     * @param position the initial grid position
     */
    public Rack(UUID id, String name, Vector2D position) {
        super(id, name, position);
        this.boxCount = 1;
        this.validDropoffIds = new ArrayList<>();
    }

    public int getBoxCount() { return boxCount; }

    /**
     * Sets the box count, clamping to a minimum of 1.
     *
     * @param boxCount the desired count; values below 1 are treated as 1
     */
    public void setBoxCount(int boxCount) {
        // a rack always has at least 1 box; 0 would mean nothing to pick up
        this.boxCount = Math.max(1, boxCount);
    }

    public List<UUID> getValidDropoffIds() { return validDropoffIds; }

    /**
     * Sets the list of valid dropoff station IDs for this rack; a {@code null} argument is treated as empty.
     *
     * @param validDropoffIds the list of delivery station UUIDs, or {@code null} to clear
     */
    public void setValidDropoffIds(List<UUID> validDropoffIds) {
        this.validDropoffIds = validDropoffIds != null ? validDropoffIds : new ArrayList<>();
    }

    public boolean isManualDropoffAssignment() { return manualDropoffAssignment; }
    public void setManualDropoffAssignment(boolean manualDropoffAssignment) {
        this.manualDropoffAssignment = manualDropoffAssignment;
    }
}
