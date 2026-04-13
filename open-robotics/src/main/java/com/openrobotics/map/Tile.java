package com.openrobotics.map;

/** single grid cell in the warehouse map; tracks occupancy and delivery station status */
public class Tile {
    private final int x;
    private final int y;
    private boolean isOccupied;
    private int visitCount; // tracks how many times a robot has visited this tile
    private boolean isDeliveryStation; // true if this tile is a delivery station (allows overlap)

    public Tile(int x, int y) {
        this.x = x;
        this.y = y;
        this.isOccupied = false;
        this.visitCount = 0;
        this.isDeliveryStation = false;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isOccupied() { return isOccupied; }
    public boolean isDeliveryStation() { return isDeliveryStation; }

    public void setOccupied(boolean occupied) { this.isOccupied = occupied; }
    public void setDeliveryStation(boolean deliveryStation) { this.isDeliveryStation = deliveryStation; }

    public int getVisitCount() { return visitCount; }
    public void incrementVisitCount() { visitCount++; }
    public void resetVisitCount() { visitCount = 0; }

    /** current position as a new Vector2D; allocates a new object on every call */
    public Vector2D getPosition() {
        return new Vector2D(x, y);
    }

}