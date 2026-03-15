package com.openrobotics.robot.sensors;

import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import java.util.List;

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

    public boolean isObstacleDetectedAt(Vector2D pos) {
        return detectedEntities.stream().anyMatch(e -> e.getPosition().equals(pos));
    }
}