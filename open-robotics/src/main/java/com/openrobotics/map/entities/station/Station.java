package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.MapEntity;

import java.util.UUID;

/** abstract base for station-type map entities (charging and delivery) */
public abstract class Station extends MapEntity {
    private boolean isBusy;

    public Station(String name, Vector2D position) {
        super(name, position);
        this.isBusy = false;
    }

    public Station(UUID id, String name, Vector2D position) {
        super(id, name, position);
        this.isBusy = false;
    }

    public boolean isBusy() { return isBusy; }

    public void setBusy(boolean busy) {
        this.isBusy = busy;
    }
}
