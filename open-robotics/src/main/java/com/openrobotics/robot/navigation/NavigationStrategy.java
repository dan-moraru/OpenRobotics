package com.openrobotics.robot.navigation;

import com.openrobotics.map.Map;

import com.openrobotics.robot.AlgorithmType;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.MoveIntention;

/** Strategy pattern interface for robot navigation. */
public interface NavigationStrategy {

    /**
     * Returns the robot's intended move for the current tick.
     *
     * @param robot the robot requesting the move
     * @param map the current warehouse map
     * @return the move intention for this tick
     */
    MoveIntention getNextMove(Robot robot, Map map);

    /**
     * Called during deadlock recovery so the strategy can discard any saved search state for this robot.
     *
     * @param robot the robot whose state should be cleared
     */
    default void reset(Robot robot) {}

    /**
     * Creates a navigation strategy for the given algorithm type; unknown or {@code NONE} falls back
     * to GREEDY so robots are always moveable.
     *
     * @param algo the algorithm type, or {@code null} to use GREEDY
     * @param seed the base seed for deterministic tie-breaking
     * @return a new {@link NavigationStrategy} instance for the given type
     */
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
