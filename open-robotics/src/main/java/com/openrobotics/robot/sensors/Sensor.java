package com.openrobotics.robot.sensors;

import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** result of a single sensor scan; exposes detected entities and spatial query methods */
public class Sensor {
    private final List<MapEntity> detectedEntities;

    public Sensor(List<MapEntity> entities) {
        this.detectedEntities = entities;
    }

    public List<MapEntity> getDetectedEntities() {
        return detectedEntities;
    }

    /** true if an Obstacle entity was detected at the given position */
    public boolean isObstacleDetectedAt(Vector2D pos) {
        for (MapEntity e : detectedEntities) {
            if (e instanceof Obstacle && e.getPosition().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** positions of all detected Obstacle entities; convenience for navigation strategies */
    public Set<Vector2D> getObstaclePositions() {
        Set<Vector2D> positions = new HashSet<>();
        for (MapEntity e : detectedEntities) {
            if (e instanceof Obstacle) positions.add(e.getPosition());
        }
        return positions;
    }

    /**
     * steps from {@code from} toward {@code toward} that are free of detected obstacles.
     * tiles beyond the sensor's scan range are treated as unknown (not clear) — a PROXIMITY robot
     * always returns maxDist, while a RANGE robot returns the real value (1–5)
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