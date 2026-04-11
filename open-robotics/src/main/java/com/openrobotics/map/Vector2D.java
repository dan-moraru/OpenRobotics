package com.openrobotics.map;

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
        return new Vector2D(Math.addExact(this.x, dx), Math.addExact(this.y, dy));
    }

    // manhattan distance for grid-based pathfinding
    public int manhattanDistance(Vector2D other) {
        Objects.requireNonNull(other, "other must not be null");
        long dx = (long) this.x - (long) other.x;
        long dy = (long) this.y - (long) other.y;
        long distance = Math.abs(dx) + Math.abs(dy);
        if (distance > Integer.MAX_VALUE) {
            throw new ArithmeticException("manhattan distance overflow: " + distance);
        }
        return (int) distance;
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
