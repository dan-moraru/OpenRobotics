package com.openrobotics;

// robot state machine states (design doc 3.4.3)
public enum RobotState {
    IDLE, // no task, waiting for assignment
    MOVING, // navigating toward pickup or dropoff location
    LOADING, // robot is at a rack picking up a box
    UNLOADING, // robot is at delivery station, dropping off box
    CHARGING // replenishing battery
}
