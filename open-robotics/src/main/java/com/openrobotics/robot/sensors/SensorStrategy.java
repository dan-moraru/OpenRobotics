package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;

/** strategy interface for sensor scanning; implementations determine scan range and detection logic */
public interface SensorStrategy {
    /** scans the environment around the robot and returns all detected entities */
    Sensor scan(Robot robot, Map map);
}