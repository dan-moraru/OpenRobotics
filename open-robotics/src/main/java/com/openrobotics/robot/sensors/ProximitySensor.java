package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import java.util.ArrayList;
import java.util.List;

/** Proximity sensor; scans all 8 tiles immediately surrounding the robot (range = 1). */
public class ProximitySensor implements SensorStrategy {
    private final int range = 1;

    @Override
    public Sensor scan(Robot robot, Map map) {
        List<MapEntity> found = new ArrayList<>();
        Vector2D currentPos = robot.getPosition();

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (dx == 0 && dy == 0) {
                    continue; // skip the robot's own tile
                }

                Vector2D checkPos = new Vector2D(currentPos.getX() + dx, currentPos.getY() + dy);

                List<MapEntity> entitiesAtTile = map.getEntitiesAt(checkPos);
                if (entitiesAtTile != null) {
                    found.addAll(entitiesAtTile);
                }
            }
        }
        return new Sensor(found);
    }

    @Override
    public String toString() {
        return "PROXIMITY";
    }
}