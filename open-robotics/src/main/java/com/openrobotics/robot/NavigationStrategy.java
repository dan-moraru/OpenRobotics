package com.openrobotics.robot;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;

import com.openrobotics.simulationcore.MoveIntention;

// strategy pattern interface for robot navigation (uml 3.3.4 / 3.4.5)
// concrete implementations include greedy; bug and rta* are future work
public interface NavigationStrategy {
    // returns the robot's intended move for the current tick
    MoveIntention getNextMove(Robot robot, Map map);
}
