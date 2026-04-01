package com.openrobotics.map;

// simple tile in the warehouse map grid
public class Tile {
    private final int x;
    private final int y;
    private boolean isOccupied;
    private int visitCount; // tracks how many times a robot has visited this tile

    public Tile(int x, int y) {
        this.x = x;
        this.y = y;
        this.isOccupied = false;
        this.visitCount = 0;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isOccupied() { return isOccupied; }

    public void setOccupied(boolean occupied) { this.isOccupied = occupied; }

    public int getVisitCount() { return visitCount; }
    public void incrementVisitCount() { visitCount++; }
    public void resetVisitCount() { visitCount = 0; }

    // convenience method returning position as vector2d
    public Vector2D getPosition() {
        return new Vector2D(x, y);
    }
}
