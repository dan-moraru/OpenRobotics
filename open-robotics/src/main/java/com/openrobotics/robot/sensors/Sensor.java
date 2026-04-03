package com.openrobotics.robot.sensors;

import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sensor holds the information of the information found from getting a scan from a selected strategy
 */
public class Sensor {
    private final List<MapEntity> detectedEntities;

    public Sensor(List<MapEntity> entities) {
        this.detectedEntities = entities;
    }

    public List<MapEntity> getDetectedEntities() {
        return detectedEntities;
    }

    // returns true only if an obstacle (not any entity) was detected at pos
    public boolean isObstacleDetectedAt(Vector2D pos) {
        for (MapEntity e : detectedEntities) {
            if (e instanceof Obstacle && e.getPosition().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    // returns the set of positions where an obstacle was detected. convenience for nav strategies
    public Set<Vector2D> getObstaclePositions() {
        Set<Vector2D> positions = new HashSet<>();
        for (MapEntity e : detectedEntities) {
            if (e instanceof Obstacle) positions.add(e.getPosition());
        }
        return positions;
    }

    // returns how many consecutive steps toward (from tile -> toward candidate tile) are free of detected obstacles.
    // tiles the sensor never scanned are treated as unknown, not clear, so a PROXIMITY robot
    // (max range 1) always gets maxDist back, while a RANGE robot (max range 5) gets the real value
    public int knownClearanceToward(Vector2D from, Vector2D toward, int maxDist) {
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