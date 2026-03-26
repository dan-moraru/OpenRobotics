package com.openrobotics;

import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.simulationcore.MoveIntention;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrafficRulesPolicyTest {

    @Test
    void givesIntersectionPriorityToRobotWithLongerStopTime() {
        Map map = new Map(3, 3);
        TrafficRulesPolicy policy = new TrafficRulesPolicy(Set.of(map.getTile(1, 1)));

        Robot shortWait = unloadedRobot("short-wait",
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                new Vector2D(0, 1),
                new Vector2D(2, 1),
                1);
        Robot longWait = unloadedRobot("long-wait",
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                new Vector2D(1, 0),
                new Vector2D(1, 2),
                5);

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, shortWait, 0, 1, 1, 1),
                move(map, longWait, 1, 0, 1, 1)
        });

        assertMove(result, shortWait, 0, 1);
        assertMove(result, longWait, 1, 1);
    }

    @Test
    void givesIntersectionPriorityToLoadedRobotWhenStopTimeIsTied() {
        Map map = new Map(3, 3);
        TrafficRulesPolicy policy = new TrafficRulesPolicy(Set.of(map.getTile(1, 1)));

        Robot unloaded = unloadedRobot("unloaded",
                UUID.fromString("00000000-0000-0000-0000-000000000021"),
                new Vector2D(0, 1),
                new Vector2D(2, 1),
                3);
        Robot loaded = loadedRobot("loaded",
                UUID.fromString("00000000-0000-0000-0000-000000000022"),
                new Vector2D(1, 0),
                new Vector2D(1, 2),
                3);

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, unloaded, 0, 1, 1, 1),
                move(map, loaded, 1, 0, 1, 1)
        });

        assertMove(result, unloaded, 0, 1);
        assertMove(result, loaded, 1, 1);
    }

    @Test
    void keepsIntersectionExclusiveUntilTheActiveRobotLeaves() {
        Map map = new Map(3, 3);
        TrafficRulesPolicy policy = new TrafficRulesPolicy(Set.of(map.getTile(1, 1)));

        Robot queuedRobot = unloadedRobot("queued",
                UUID.fromString("00000000-0000-0000-0000-000000000041"),
                new Vector2D(0, 1),
                new Vector2D(2, 1),
                1);
        Robot activeRobot = unloadedRobot("active",
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                new Vector2D(1, 0),
                new Vector2D(1, 2),
                4);

        MoveIntention[] tickOne = policy.apply(map, new MoveIntention[] {
                move(map, queuedRobot, 0, 1, 1, 1),
                move(map, activeRobot, 1, 0, 1, 1)
        });
        assertMove(tickOne, queuedRobot, 0, 1);
        assertMove(tickOne, activeRobot, 1, 1);

        activeRobot.setPosition(new Vector2D(1, 1));

        MoveIntention[] tickTwo = policy.apply(map, new MoveIntention[] {
                move(map, queuedRobot, 0, 1, 1, 1),
                move(map, activeRobot, 1, 1, 1, 2)
        });
        assertMove(tickTwo, queuedRobot, 0, 1);
        assertMove(tickTwo, activeRobot, 1, 2);

        activeRobot.setPosition(new Vector2D(1, 2));

        MoveIntention[] tickThree = policy.apply(map, new MoveIntention[] {
                move(map, queuedRobot, 0, 1, 1, 1),
                stay(map, activeRobot, 1, 2)
        });
        assertMove(tickThree, queuedRobot, 1, 1);
    }

    private static Robot unloadedRobot(String name, UUID id, Vector2D start, Vector2D pickupTarget, int stuckTicks) {
        Robot robot = new Robot(id, name, start);
        robot.setCurrentTask(new Task(id.hashCode(), pickupTarget, pickupTarget, 1));
        robot.setState(RobotState.MOVING);
        robot.setStuckTicks(stuckTicks);
        return robot;
    }

    private static Robot loadedRobot(String name, UUID id, Vector2D start, Vector2D dropoffTarget, int stuckTicks) {
        Robot robot = new Robot(id, name, start);
        robot.setCurrentTask(new Task(id.hashCode(), start, dropoffTarget, 1));
        robot.setHasPickedUp(true);
        robot.setState(RobotState.MOVING);
        robot.setStuckTicks(stuckTicks);
        return robot;
    }

    private static MoveIntention move(Map map, Robot robot, int fromX, int fromY, int toX, int toY) {
        return new MoveIntention(map.getTile(fromX, fromY), map.getTile(toX, toY), robot);
    }

    private static MoveIntention stay(Map map, Robot robot, int x, int y) {
        Tile tile = map.getTile(x, y);
        return new MoveIntention(tile, tile, robot);
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
}
