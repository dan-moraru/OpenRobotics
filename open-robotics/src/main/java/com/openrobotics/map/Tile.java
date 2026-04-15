package com.openrobotics.map;

/** Single grid cell in the warehouse map; tracks occupancy and station-overlap flags. */
public class Tile {
    private final int x;
    private final int y;
    private boolean isOccupied;
    private int visitCount; // tracks how many times a robot has visited this tile
    private boolean isDeliveryStation;
    private boolean isChargingStation;

    public Tile(int x, int y) {
        this.x = x;
        this.y = y;
        this.isOccupied = false;
        this.visitCount = 0;
        this.isDeliveryStation = false;
        this.isChargingStation = false;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isOccupied() { return isOccupied; }
    public boolean isDeliveryStation() { return isDeliveryStation; }
    public boolean isChargingStation() { return isChargingStation; }

    /**
     * Returns true if robots may overlap on this tile without triggering a collision.
     *
     * @return {@code true} if this tile is a delivery station or a charging station
     */
    public boolean allowsRobotOverlap() { return isDeliveryStation || isChargingStation; }

    public void setOccupied(boolean occupied) { this.isOccupied = occupied; }
    public void setDeliveryStation(boolean deliveryStation) { this.isDeliveryStation = deliveryStation; }
    public void setChargingStation(boolean chargingStation) { this.isChargingStation = chargingStation; }

    public int getVisitCount() { return visitCount; }
    public void incrementVisitCount() { visitCount++; }
    public void resetVisitCount() { visitCount = 0; }

    /**
     * Returns the current position as a new {@code Vector2D}; allocates a new object on every call.
     *
     * @return a new {@code Vector2D} at (x, y)
     */
    public Vector2D getPosition() {
        return new Vector2D(x, y);
    }
}
