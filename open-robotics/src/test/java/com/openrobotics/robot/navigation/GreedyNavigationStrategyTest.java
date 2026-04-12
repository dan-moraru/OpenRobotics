package com.openrobotics.robot.navigation;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.sensors.Sensor;
import com.openrobotics.simulationcore.MoveIntention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GreedyNavigationStrategy}.
 *
 * <p>Focuses on stay behavior, greedy progress, sensor-aware tie-breaking,
 * visited/backtracking behavior, target resets, and configuration labeling.
 */
public class GreedyNavigationStrategyTest {

    private Map map;
    private GreedyNavigationStrategy strategy;

    private static class TestRobot extends Robot {
        private Vector2D target;
        private Sensor scan;

        private TestRobot(String name, Vector2D position) {
            super(name, position);
        }

        public void setTargetForTest(Vector2D target) {
            this.target = target;
        }

        public void setScanForTest(Sensor scan) {
            this.scan = scan;
        }

        @Override
        public Vector2D getTarget() {
            return target;
        }

        @Override
        public Sensor getLastScan() {
            return scan;
        }
    }

    @BeforeEach
    public void setUp() {
        map = new Map(10, 10);
        strategy = new GreedyNavigationStrategy(42L);
    }

    private TestRobot makeRobot(int x, int y) {
        return new TestRobot("GreedyBot", new Vector2D(x, y));
    }

    /**
     * Without a target, the robot should wait in place.
     */
    @Test
    public void testNoTargetReturnsStayIntention() {
        TestRobot robot = makeRobot(2, 2);

        MoveIntention move = strategy.getNextMove(robot, map);

        assertSame(move.getFromTile(), move.getToTile());
    }

    /**
     * If already at target, the strategy should also wait.
     */
    @Test
    public void testAtTargetReturnsStayIntention() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(2, 2));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertSame(move.getFromTile(), move.getToTile());
    }

    /**
     * On a clear map, Greedy should move to a neighbor that reduces Manhattan distance.
     */
    @Test
    public void testClearMapMovesCloserToTarget() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(5, 2));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(3, 2), move.getToTile().getPosition());
    }

    /**
     * Sensor-reported obstacles at the target tile are ignored so the robot can still step onto the goal.
     */
    @Test
    public void testSensorObstacleAtTargetDoesNotBlockArrival() {
        TestRobot robot = makeRobot(2, 2);
        Vector2D target = new Vector2D(3, 2);
        robot.setTargetForTest(target);
        robot.setScanForTest(new Sensor(List.of(new Obstacle("TargetObstacle", target))));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(target, move.getToTile().getPosition());
    }

    /**
     * Sensor clearance should break ties between equally good candidates.
     */
    @Test
    public void testSensorClearanceBreaksTieBetweenClosestCandidates() {
        TestRobot robot = makeRobot(1, 1);
        robot.setTargetForTest(new Vector2D(2, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("AheadOfRight", new Vector2D(3, 1))
        )));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(1, 2), move.getToTile().getPosition());
    }

    /**
     * Real map obstacles should be avoided when selecting the next step.
     */
    @Test
    public void testMapObstacleIsAvoided() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        map.addEntity(new Obstacle("Wall", new Vector2D(3, 2)));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertNotEquals(new Vector2D(3, 2), move.getToTile().getPosition());
    }

    /**
     * When the best-looking step is blocked by sensors, the strategy should detour instead of staying immediately.
     */
    @Test
    public void testSensorBlockedForwardPathCanDetour() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("BlockedAhead", new Vector2D(3, 2))
        )));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertNotEquals(new Vector2D(3, 2), move.getToTile().getPosition());
        assertNotSame(move.getFromTile(), move.getToTile());
    }

    /**
     * If every traversable neighbor has already been visited, the strategy backtracks rather than getting stuck.
     */
    @Test
    public void testBacktracksWhenOnlyVisitedNeighborsRemain() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));

        MoveIntention first = strategy.getNextMove(robot, map);
        assertEquals(new Vector2D(3, 2), first.getToTile().getPosition());

        robot.setPosition(new Vector2D(3, 2));
        MoveIntention second = strategy.getNextMove(robot, map);
        assertEquals(new Vector2D(4, 2), second.getToTile().getPosition());

        robot.setPosition(new Vector2D(3, 2));
        map.addEntity(new Obstacle("RightBlocked", new Vector2D(4, 2)));
        map.addEntity(new Obstacle("UpBlocked", new Vector2D(3, 1)));
        map.addEntity(new Obstacle("DownBlocked", new Vector2D(3, 3)));

        MoveIntention third = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 2), third.getToTile().getPosition(),
                "Greedy should backtrack to the prior path stack position");
    }

    /**
     * Changing the target should reset visited/path state and immediately work toward the new target.
     */
    @Test
    public void testTargetChangeResetsState() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        strategy.getNextMove(robot, map);

        robot.setTargetForTest(new Vector2D(2, 0));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 1), move.getToTile().getPosition());
    }

    /**
     * toString() should expose the configuration label used by save/load code.
     */
    @Test
    public void testToString() {
        assertEquals("GREEDY", strategy.toString());
    }
}
