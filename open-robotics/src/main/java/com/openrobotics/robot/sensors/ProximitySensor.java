package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import java.util.ArrayList;
import java.util.List;

/**
 * ProximitySensor strategy detects surroundings by a proximity sensor.
 * Range is set to 1, so that the robot can look in the 3x3 area around him.
 */
public class ProximitySensor implements SensorStrategy {
    private final int range = 1; // Distance of range

    @Override
    public Sensor scan(Robot robot, Map map) {
        List<MapEntity> found = new ArrayList<>();
        Vector2D currentPos = robot.getPosition();

        // Scan the area around the robot
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (dx == 0 && dy == 0) {
                    continue; // Dont check self, so skip
                }

                Vector2D checkPos = new Vector2D(currentPos.getX() + dx, currentPos.getY() + dy);

                // Get entities from the map at that specific tile
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