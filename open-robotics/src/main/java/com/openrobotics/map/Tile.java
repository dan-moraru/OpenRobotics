package com.openrobotics.map;

// simple tile in the warehouse map grid
public class Tile {
    private final int x;
    private final int y;
    private boolean isOccupied;

    public Tile(int x, int y) {
        this.x = x;
        this.y = y;
        this.isOccupied = false;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isOccupied() { return isOccupied; }

    public void setOccupied(boolean occupied) { this.isOccupied = occupied; }

    // convenience method returning position as vector2d
    public Vector2D getPosition() {
        return new Vector2D(x, y);
    }
}
