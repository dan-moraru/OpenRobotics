package com.openrobotics.robot.navigation;

import com.openrobotics.map.Map;

import com.openrobotics.robot.AlgorithmType;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.MoveIntention;

// strategy pattern interface for robot navigation (uml 3.3.4 / 3.4.5)
public interface NavigationStrategy {
    // returns the robot's intended move for the current tick
    MoveIntention getNextMove(Robot robot, Map map);

    // Recovery hook so strategies can forget saved search for one robot
    default void reset(Robot robot) {}

    // Factory: create a navigation strategy for the given algorithm type.
    // Unknown or NONE falls back to GREEDY so robots are always moveable.
    static NavigationStrategy create(AlgorithmType algo, long seed) {
        if (algo == null) return new GreedyNavigationStrategy(seed);
        return switch (algo) {
            case BUG -> new BugNavigationStrategy(seed);
            case RTA_STAR -> new RtaStarNavigationStrategy(seed);
            case RANDOM -> new RandomNavigation();
            case GREEDY, NONE -> new GreedyNavigationStrategy(seed);
        };
    }
}
