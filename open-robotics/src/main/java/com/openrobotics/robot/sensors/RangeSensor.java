package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RangeSensor strategy detects surroundings using a vision based system, similarly to LiDAR or ray-casting.
 * Keeps track of the maximum range, the ray count and step size of the search.
 */
public class RangeSensor implements SensorStrategy {
    private final double maxRange = 5.0;
    private final int rayCount = 15; // Number of rays (90 degrees / 15 = 1 ray every 6 deg)
    private final double stepSize = 0.2;

    @Override
    public Sensor scan(Robot robot, Map map) {
        Set<MapEntity> detected = new HashSet<>();
        Vector2D currentPos = robot.getPosition();

        // Get hypothetical angle the robot is currently facing
        double facingAngle = calculateHeading(robot);

        // Cast rays within a 90 degree arc (PI/2 radians)
        double startAngle = facingAngle - (Math.PI / 4); // -45 degrees
        double endAngle = facingAngle + (Math.PI / 4); // +45 degrees
        double angleStep = (endAngle - startAngle) / (rayCount - 1);

        for (int i = 0; i < rayCount; i++) {
            double currentAngle = startAngle + (i * angleStep);

            for (double dist = stepSize; dist <= maxRange; dist += stepSize) {
                int gridX = (int) Math.round(currentPos.getX() + dist * Math.cos(currentAngle));
                int gridY = (int) Math.round(currentPos.getY() + dist * Math.sin(currentAngle));

                if (gridX < 0 || gridX >= map.getWidth() || gridY < 0 || gridY >= map.getHeight()) {
                    break;
                }

                List<MapEntity> hits = map.getEntitiesAt(new Vector2D(gridX, gridY));
                for (MapEntity entity : hits) {
                    if (entity != null && entity != robot) {
                        detected.add(entity);
                        break;
                    }
                }
            }
        }

        return new Sensor((List<MapEntity>) detected);
    }

    // Calculates rough robot heading (not entirely precise) for ray-casting
    private double calculateHeading(Robot robot) {
        Vector2D last = robot.getPreviousPosition();
        Vector2D current = robot.getPosition();

        if (last == null || last.equals(current)) {
            return 0;
        }

        // Returns angle in radians between the two points
        return Math.atan2(current.getY() - last.getY(), current.getX() - last.getX());
    }

    @Override
    public String toString() {
        return "RANGE";
    }
}
