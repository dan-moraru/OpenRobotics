package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.SensorType;

/** Strategy interface for sensor scanning; implementations determine scan range and detection logic. */
public interface SensorStrategy {

    /**
     * Scans the environment around the robot and returns all detected entities.
     *
     * @param robot the scanning robot
     * @param map the current warehouse map
     * @return a {@link Sensor} containing all detected entities
     */
    Sensor scan(Robot robot, Map map);

    /**
     * Creates a sensor strategy for the given sensor type; unknown or {@code NONE} falls back to
     * PROXIMITY so robots always have a sensor.
     *
     * @param type the sensor type, or {@code null} to use PROXIMITY
     * @return a new {@link SensorStrategy} instance for the given type
     */
    static SensorStrategy create(SensorType type) {
        if (type == null) return new ProximitySensor();
        return switch (type) {
            case RANGE -> new RangeSensor();
            case PROXIMITY, NONE -> new ProximitySensor();
        };
    }
}
