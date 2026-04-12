package com.openrobotics.robot.navigation;

import com.openrobotics.map.Map;

import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.MoveIntention;

// strategy pattern interface for robot navigation (uml 3.3.4 / 3.4.5)
public interface NavigationStrategy {
    // returns the robot's intended move for the current tick
    MoveIntention getNextMove(Robot robot, Map map);

    // Recovery hook so strategies can forget saved search for one robot
    default void reset(Robot robot) {}
}
