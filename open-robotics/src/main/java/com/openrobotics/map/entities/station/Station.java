package com.openrobotics.map.entities.station;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.MapEntity;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// abstract base for station-type entities (uml 3.3.4)
public abstract class Station extends MapEntity {
    private final AtomicBoolean isBusy;

    public Station(String name, Vector2D position) {
        super(name, position);
        this.isBusy = new AtomicBoolean(false);
    }

    // Constructor for loading stations
    public Station(UUID id, String name, Vector2D position) {
        super(id, name, position);
        this.isBusy = new AtomicBoolean(false);
    }

    public boolean isBusy() { return isBusy.get(); }

    public boolean tryAcquire() {
        return isBusy.compareAndSet(false, true);
    }

    public void release() {
        isBusy.set(false);
    }

    public void setBusy(boolean busy) {
        isBusy.set(busy);
    }
}
