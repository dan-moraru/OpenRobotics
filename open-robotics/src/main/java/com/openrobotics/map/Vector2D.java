package com.openrobotics.map;

import java.util.Objects;

/** Immutable 2-D integer position used for all grid coordinates throughout the system. */
public class Vector2D {
    private final int x;
    private final int y;

    public Vector2D(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }

    /**
     * Returns a new vector offset by (dx, dy); this instance is unchanged.
     *
     * @param dx the x delta to add
     * @param dy the y delta to add
     * @return a new {@code Vector2D} at (x + dx, y + dy)
     * @throws ArithmeticException if the addition overflows int
     */
    public Vector2D add(int dx, int dy) {
        return new Vector2D(Math.addExact(this.x, dx), Math.addExact(this.y, dy));
    }

    /**
     * Returns the Manhattan distance to {@code other}.
     *
     * @param other the target position
     * @return the non-negative integer distance
     * @throws ArithmeticException if the result overflows int
     */
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
