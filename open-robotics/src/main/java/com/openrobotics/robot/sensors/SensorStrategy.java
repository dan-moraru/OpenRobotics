package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.SensorType;

/**
 * SensorStrategy Interface, allows different types of strategies to scan for sensors
 */
public interface SensorStrategy {
    // Get the scan from a robot and the map
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