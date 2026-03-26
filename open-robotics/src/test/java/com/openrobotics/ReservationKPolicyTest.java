package com.openrobotics;

import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.robot.navigation.NavigationStrategy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.MoveIntention;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservationKPolicyTest {

    @Test
    void blocksMovesIntoFartherTilesReservedByTheBfsWindow() {
        Map map = new Map(5, 1);
        ReservationKPolicy policy = new ReservationKPolicy(3);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));
        Robot robotB = movingRobot("robot-b",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new Vector2D(4, 0),
                new Vector2D(0, 0));

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, robotA, 0, 0, 1, 0),
                move(map, robotB, 4, 0, 3, 0)
        });

        // Robot A reserves 1,0 -> 2,0 -> 3,0, so robot B must wait at 4,0.
        assertMove(result, robotA, 1, 0);
        assertMove(result, robotB, 4, 0);
    }

    @Test
    void releasesConsumedReservationsAsTheRobotProgresses() {
        Map map = new Map(5, 1);
        ReservationKPolicy policy = new ReservationKPolicy(3);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));

        // First tick: create reservations [1,0], [2,0], [3,0].
        policy.apply(map, new MoveIntention[] {
                move(map, robotA, 0, 0, 1, 0)
        });

        // Simulate the approved move being committed before the next tick.
        robotA.setPosition(new Vector2D(1, 0));

        Robot robotC = movingRobot("robot-c",
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                new Vector2D(0, 0),
                new Vector2D(1, 0));

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, robotA, 1, 0, 2, 0),
                move(map, robotC, 0, 0, 1, 0)
        });

        // Tile 1,0 should have been released after robot A reached it.
        // Robot C only needs that one tile, so it can now reserve and enter it.
        assertMove(result, robotA, 2, 0);
        assertMove(result, robotC, 1, 0);
    }

    @Test
    void simulationEngineAppliesReservationPolicyBeforeCollisionResolution() {
        Map map = new Map(5, 1);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));
        robotA.setNav(new CorridorStepNavigation());

        Robot robotB = movingRobot("robot-b",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new Vector2D(4, 0),
                new Vector2D(0, 0));
        robotB.setNav(new CorridorStepNavigation());

        map.addEntity(robotA);
        map.addEntity(robotB);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[] { robotA, robotB },
                new Dispatcher(),
                new ReservationKPolicy(3)
        );

        engine.tick();

        // Without ReservationK being applied in the engine, robot B would move to 3,0.
        assertEquals(new Vector2D(1, 0), robotA.getPosition());
        assertEquals(new Vector2D(4, 0), robotB.getPosition());
    }

    private static Robot movingRobot(String name, UUID id, Vector2D start, Vector2D target) {
        Robot robot = new Robot(id, name, start);
        robot.setCurrentTask(new Task(id.hashCode(), target, target, 1));
        robot.setState(RobotState.MOVING);
        return robot;
    }

    private static MoveIntention move(Map map, Robot robot, int fromX, int fromY, int toX, int toY) {
        Tile from = map.getTile(fromX, fromY);
        Tile to = map.getTile(toX, toY);
        return new MoveIntention(from, to, robot);
    }

    private static void assertMove(MoveIntention[] intentions, Robot robot, int expectedX, int expectedY) {
        for (MoveIntention intention : intentions) {
            if (intention.getRobot().getId().equals(robot.getId())) {
                assertEquals(expectedX, intention.getToTile().getX());
                assertEquals(expectedY, intention.getToTile().getY());
                return;
            }
        }

        throw new AssertionError("No move intention found for robot " + robot.getName());
    }

    // Simple deterministic test navigation for the one-row corridor.
    private static class CorridorStepNavigation implements NavigationStrategy {
        @Override
        public MoveIntention getNextMove(Robot robot, Map map) {
            Tile fromTile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            Vector2D target = robot.getTarget();
            int dx = Integer.compare(target.getX(), robot.getPosition().getX());
            Tile toTile = map.getTile(robot.getPosition().getX() + dx, robot.getPosition().getY());
            return new MoveIntention(fromTile, toTile, robot);
        }
    }
}
