package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.SensorType;

/** strategy interface for sensor scanning; implementations determine scan range and detection logic */
public interface SensorStrategy {
    /** scans the environment around the robot and returns all detected entities */
    Sensor scan(Robot robot, Map map);

    // Factory: create a sensor strategy for the given sensor type.
    // Unknown or NONE falls back to PROXIMITY so robots always have a sensor.
    static SensorStrategy create(SensorType type) {
        if (type == null) return new ProximitySensor();
        return switch (type) {
            case RANGE -> new RangeSensor();
            case PROXIMITY, NONE -> new ProximitySensor();
        };
    }
}