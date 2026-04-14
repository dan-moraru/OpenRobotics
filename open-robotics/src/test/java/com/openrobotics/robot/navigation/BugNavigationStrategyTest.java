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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BugNavigationStrategy}.
 *
 * <p>Focuses on stay behavior, greedy progress, sensor-aware tie-breaking,
 * boundary-following entry/reset behavior, and the current collision-rejection recovery path.
 */
public class BugNavigationStrategyTest {

    private Map map;
    private BugNavigationStrategy strategy;

    private static class TestRobot extends Robot {
        private Vector2D target;
        private Sensor scan;

        private TestRobot(String name, Vector2D position) {
            super(name, position);
        }

        private TestRobot(UUID id, String name, Vector2D position) {
            super(id, name, position);
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
        strategy = new BugNavigationStrategy(42L);
    }

    private TestRobot makeRobot(int x, int y) {
        return new TestRobot("BugBot", new Vector2D(x, y));
    }

    private TestRobot makeRobot(UUID id, int x, int y) {
        return new TestRobot(id, "BugBot", new Vector2D(x, y));
    }

    /**
     * Without a target, the robot should wait in place.
     */
    @Test
    public void testNoTargetReturnsStayIntention() {
        TestRobot robot = makeRobot(2, 2);

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 2), move.getFromTile().getPosition());
        assertSame(move.getFromTile(), move.getToTile());
    }

    /**
     * If the robot is already at its target, the strategy should also wait.
     */
    @Test
    public void testAtTargetReturnsStayIntention() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(2, 2));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertSame(move.getFromTile(), move.getToTile());
    }

    /**
     * On a clear map, Bug should make a greedy move that reduces Manhattan distance.
     */
    @Test
    public void testGreedyStepOnClearMapMovesCloserToTarget() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(5, 2));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(3, 2), move.getToTile().getPosition());
    }

    /**
     * Sensor-reported obstacles at the target tile are ignored so the robot can still arrive.
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
     * Sensor clearance should break ties between equally good greedy candidates.
     */
    @Test
    public void testSensorClearanceBreaksTieBetweenCloserCandidates() {
        TestRobot robot = makeRobot(1, 1);
        robot.setTargetForTest(new Vector2D(2, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("AheadOfRight", new Vector2D(3, 1))
        )));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(1, 2), move.getToTile().getPosition(),
                "Bug should prefer the clearer downward candidate over the rightward one");
    }

    /**
     * When multiple closest candidates tie on distance and sensor clearance, pickBest() still returns one of them.
     */
    @Test
    public void testPickBestReturnsOneOfTiedClosestCandidates() {
        TestRobot robot = makeRobot(1, 1);
        robot.setTargetForTest(new Vector2D(2, 2));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertTrue(
                move.getToTile().getPosition().equals(new Vector2D(2, 1))
                        || move.getToTile().getPosition().equals(new Vector2D(1, 2)),
                "With equal distance and no sensor preference, Bug should choose one of the tied best moves"
        );
    }

    /**
     * When tied best candidates also tie on sensor clearance, pickBest() should keep both and
     * the strategy should choose one of them via the seeded-random final tie-break.
     */
    @Test
    public void testPickBestUsesRandomTieBreakAfterSensorClearanceTie() {
        TestRobot robot = makeRobot(
                UUID.fromString("00000000-0000-0000-0000-000000000123"),
                1, 1);
        robot.setTargetForTest(new Vector2D(2, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("AheadOfRight", new Vector2D(3, 1)),
                new Obstacle("AheadOfDown", new Vector2D(1, 3))
        )));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertTrue(
                move.getToTile().getPosition().equals(new Vector2D(2, 1))
                        || move.getToTile().getPosition().equals(new Vector2D(1, 2)),
                "After equal sensor-clearance filtering, Bug should choose one of the tied clearest moves"
        );
    }

    /**
     * When no greedy closer move is available because of sensed blockage, the strategy enters wall-following.
     */
    @Test
    public void testBlockedGreedyPathEntersBoundaryFollowing() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("BlockedAhead", new Vector2D(3, 2))
        )));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 3), move.getToTile().getPosition(),
                "Left-hand boundary following should step down first in this setup");
    }

    /**
     * If a boundary-following move is rejected and the target later changes, the old boundary state should be discarded.
     */
    @Test
    public void testTargetChangeResetsBoundaryState() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("BlockedAhead", new Vector2D(3, 2))
        )));

        MoveIntention first = strategy.getNextMove(robot, map);
        assertEquals(new Vector2D(2, 3), first.getToTile().getPosition());

        robot.setTargetForTest(new Vector2D(2, 0));
        robot.setScanForTest(null);

        MoveIntention second = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 1), second.getToTile().getPosition(),
                "Changing target should reset Bug state and resume greedy navigation");
    }

    /**
     * If the previous intended move was rejected, the next decision should remain stable rather than drifting.
     */
    @Test
    public void testRejectedBoundaryMoveProducesStableRetry() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("BlockedAhead", new Vector2D(3, 2))
        )));

        MoveIntention first = strategy.getNextMove(robot, map);
        MoveIntention second = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 3), first.getToTile().getPosition());
        assertEquals(new Vector2D(2, 3), second.getToTile().getPosition());
    }

    /**
     * If Bug is in wall-following mode and every candidate is blocked, boundaryStep() falls back to waiting.
     */
    @Test
    public void testCompletelyStuckDuringBoundaryFollowingReturnsStayIntention() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        robot.setScanForTest(new Sensor(List.of(
                new Obstacle("BlockedAhead", new Vector2D(3, 2))
        )));

        MoveIntention first = strategy.getNextMove(robot, map);
        assertEquals(new Vector2D(2, 3), first.getToTile().getPosition());

        map.addEntity(new Obstacle("DownBlocked", new Vector2D(2, 3)));
        map.addEntity(new Obstacle("UpBlocked", new Vector2D(2, 1)));
        map.addEntity(new Obstacle("LeftBlocked", new Vector2D(1, 2)));
        map.addEntity(new Obstacle("RightBlocked", new Vector2D(3, 2)));

        MoveIntention second = strategy.getNextMove(robot, map);

        assertSame(second.getFromTile(), second.getToTile(),
                "When no boundary-following move is possible, Bug should wait");
        assertEquals(new Vector2D(2, 2), second.getToTile().getPosition());
    }

    /**
     * Real map obstacles should also trigger wall-following when they remove all greedy closer moves.
     */
    @Test
    public void testMapObstacleCanForceBoundaryFollowing() {
        TestRobot robot = makeRobot(2, 2);
        robot.setTargetForTest(new Vector2D(4, 2));
        map.addEntity(new Obstacle("Wall", new Vector2D(3, 2)));

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(2, 3), move.getToTile().getPosition());
    }

    /**
     * toString() should expose the configuration label used by save/load code.
     */
    @Test
    public void testToString() {
        assertEquals("BUG", strategy.toString());
    }
}
