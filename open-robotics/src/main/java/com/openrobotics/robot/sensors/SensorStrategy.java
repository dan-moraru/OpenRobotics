package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;

/**
 * SensorStrategy Interface, allows different types of strategies to scan for sensors
 */
public interface SensorStrategy {
    // Get the scan from a robot and the map
    Sensor scan(Robot robot, Map map);
}