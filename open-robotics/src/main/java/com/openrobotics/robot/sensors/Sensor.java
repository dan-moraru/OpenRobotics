package com.openrobotics.robot.sensors;

import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Result of a single sensor scan; exposes detected entities and spatial query methods. */
public class Sensor {
    private final List<MapEntity> detectedEntities;

    /**
     * Creates a scan result from the given list of detected entities.
     *
     * @param entities the entities detected during this scan
     */
    public Sensor(List<MapEntity> entities) {
        this.detectedEntities = entities;
    }

    public List<MapEntity> getDetectedEntities() {
        return detectedEntities;
    }

    /**
     * Returns true if an {@link Obstacle} entity was detected at the given position.
     *
     * @param pos the position to check
     * @return {@code true} if an obstacle was detected at {@code pos}
     */
    public boolean isObstacleDetectedAt(Vector2D pos) {
        for (MapEntity e : detectedEntities) {
            if (e instanceof Obstacle && e.getPosition().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the positions of all detected {@link Obstacle} entities; convenience for navigation strategies.
     *
     * @return a set of positions where obstacles were detected
     */
    public Set<Vector2D> getObstaclePositions() {
        Set<Vector2D> positions = new HashSet<>();
        for (MapEntity e : detectedEntities) {
            if (e instanceof Obstacle) positions.add(e.getPosition());
        }
        return positions;
    }

    /**
     * Returns the number of steps from {@code from} toward {@code toward} that are free of detected obstacles.
     * Tiles beyond the sensor's scan range are treated as unknown (not confirmed clear) — a PROXIMITY
     * robot always returns {@code maxDist}, while a RANGE robot returns the real value (1–5).
     *
     * @param from the robot's current position
     * @param toward the candidate next tile
     * @param maxDist the maximum distance to check (scan range cap)
     * @return the number of obstacle-free steps, up to {@code maxDist}
     */
    public int knownClearanceToward(Vector2D from, Vector2D toward, int maxDist) {
        if (from != null && from.equals(toward)) {
            return 0;
        }
        int dx = Integer.signum(toward.getX() - from.getX());
        int dy = Integer.signum(toward.getY() - from.getY());
        Set<Vector2D> obstacles = getObstaclePositions();
        for (int step = 1; step <= maxDist; step++) {
            Vector2D check = new Vector2D(from.getX() + dx * step, from.getY() + dy * step);
            if (obstacles.contains(check)) return step - 1;
        }
        return maxDist;
    }
}
