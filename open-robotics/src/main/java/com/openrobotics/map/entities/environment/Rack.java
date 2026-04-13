package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.MapEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** storage rack; robots pick up boxes from an adjacent tile, never from the rack tile itself */
public class Rack extends MapEntity {
    private int boxCount;
    private List<UUID> validDropoffIds;

    public Rack(String name, Vector2D position) {
        super(name, position);
        this.boxCount = 1;
        this.validDropoffIds = new ArrayList<>();
    }

    public Rack(UUID id, String name, Vector2D position) {
        super(id, name, position);
        this.boxCount = 1;
        this.validDropoffIds = new ArrayList<>();
    }

    public int getBoxCount() { return boxCount; }
    public void setBoxCount(int boxCount) {
        // a rack always has at least 1 box; 0 would mean nothing to pick up
        this.boxCount = Math.max(1, boxCount);
    }

    public List<UUID> getValidDropoffIds() { return validDropoffIds; }
    public void setValidDropoffIds(List<UUID> validDropoffIds) {
        this.validDropoffIds = validDropoffIds != null ? validDropoffIds : new ArrayList<>();
    }

    private boolean manualDropoffAssignment = false;

    public boolean isManualDropoffAssignment() { return manualDropoffAssignment; }
    public void setManualDropoffAssignment(boolean manualDropoffAssignment) {
        this.manualDropoffAssignment = manualDropoffAssignment;
    }

}
