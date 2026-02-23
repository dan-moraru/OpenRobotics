package com.openrobotics;

// strategy pattern interface for robot navigation (uml 3.3.4 / 3.4.5)
// concrete implementations (greedy, bug, rta*) will be done later
public interface NavigationStrategy {
    // returns the intended next position for the robot
    Vector2D makeMove(Robot robot, Map map);
}
