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
 * Unit tests for {@link RtaStarNavigationStrategy}.
 *
 * <p>Focuses on stay behavior, neighbor filtering, sensor-aware selection,
 * target resets, simple learning stability, and configuration labeling.
 */
public class RtaStarNavigationStrategyTest {

    private Map map;
    private RtaStarNavigationStrategy strategy;

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
        strategy = new RtaStarNavigationStrategy(42L);
    }

    private TestRobot makeRobot(int x, int y) {
        return new TestRobot("RtaBot", new Vector2D(x, y));
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
     * On a clear map, the chosen move should reduce Manhattan distance.
     */
    @Test
    public void testClearMapMoveGetsCloserToTarget() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(5, 2));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(3, 2), move.getToTile().getPosition());
    }

    /**
     * Sensor-reported obstacles at the target tile are ignored so the robot can still move there.
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
     * Sensor-blocked non-target neighbors should be excluded from consideration.
     */
    @Test
    public void testSensorBlockedNeighborIsSkipped() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        robot.setScanForTest(new Sensor(List.of(new Obstacle("Blocked", new Vector2D(3, 2)))));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertNotEquals(new Vector2D(3, 2), move.getToTile().getPosition());
    }

    /**
     * Sensor penalty should prefer the direction with better known clearance when distances tie.
     */
    @Test
    public void testSensorPenaltyBreaksTieTowardClearerDirection() {
        TestRobot robot = makeRobot(1, 1);
        robot.setTargetForTest(new Vector2D(2, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("AheadOfRight", new Vector2D(3, 1))
        )));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(1, 2), move.getToTile().getPosition());
    }

    /**
     * When all neighbors are blocked by real obstacles, the strategy must stay in place.
     */
    @Test
    public void testNoCandidatesReturnsStayIntention() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 4));
        map.addEntity(new Obstacle("Up", new Vector2D(2, 1)));
        map.addEntity(new Obstacle("Down", new Vector2D(2, 3)));
        map.addEntity(new Obstacle("Left", new Vector2D(1, 2)));
        map.addEntity(new Obstacle("Right", new Vector2D(3, 2)));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertSame(move.getFromTile(), move.getToTile());
    }

    /**
     * Revisiting the same state should still produce a valid one-step move after learning updates.
     */
    @Test
    public void testRepeatedCallsStillProduceLegalOneStepMoves() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));

        MoveIntention first = strategy.getNextMove(robot, map);
        MoveIntention second = strategy.getNextMove(robot, map);

        assertTrue(Math.abs(first.getToTile().getX() - first.getFromTile().getX())
                + Math.abs(first.getToTile().getY() - first.getFromTile().getY()) <= 1);
        assertTrue(Math.abs(second.getToTile().getX() - second.getFromTile().getX())
                + Math.abs(second.getToTile().getY() - second.getFromTile().getY()) <= 1);
    }

    /**
     * Changing target should reset learned state so the strategy immediately works toward the new target.
     */
    @Test
    public void testTargetChangeResetsLearnedState() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        strategy.getNextMove(robot, map);

        robot.setTargetForTest(new Vector2D(2, 0));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 1), move.getToTile().getPosition());
    }

    /**
     * Real map obstacles should be filtered out via getNeighbors().
     */
    @Test
    public void testMapObstacleIsNotChosenAsCandidate() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        map.addEntity(new Obstacle("Wall", new Vector2D(3, 2)));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertNotEquals(new Vector2D(3, 2), move.getToTile().getPosition());
    }

    /**
     * toString() should expose the configuration label used by save/load code.
     */
    @Test
    public void testToString() {
        assertEquals("RTA_STAR", strategy.toString());
    }
}
