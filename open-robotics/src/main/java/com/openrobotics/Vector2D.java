package com.openrobotics;

import java.util.Objects;

// immutable 2d position used throughout the system
public class Vector2D {
    private final int x;
    private final int y;

    public Vector2D(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }

    // returns new vector offset by dx, dy (immutable)
    public Vector2D add(int dx, int dy) {
        return new Vector2D(this.x + dx, this.y + dy);
    }

    // manhattan distance for grid-based pathfinding
    public int manhattanDistance(Vector2D other) {
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vector2D other)) return false;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
