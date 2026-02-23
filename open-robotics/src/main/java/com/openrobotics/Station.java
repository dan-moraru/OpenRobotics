package com.openrobotics;

// abstract base for station-type entities (uml 3.3.4)
public abstract class Station extends MapEntity {
    private boolean isBusy;

    public Station(String name, Vector2D position) {
        super(name, position);
        this.isBusy = false;
    }

    public boolean isBusy() { return isBusy; }
    public void setBusy(boolean busy) { this.isBusy = busy; }
}
