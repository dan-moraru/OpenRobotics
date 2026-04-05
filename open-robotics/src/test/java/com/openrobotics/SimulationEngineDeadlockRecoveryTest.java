package com.openrobotics;

import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.robot.navigation.NavigationStrategy;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.MoveIntention;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineDeadlockRecoveryTest {

    @Test
    void firstDeadlockAttemptsARerouteWithoutDroppingTheTask() {
        Map map = new Map(3, 3);
        Dispatcher dispatcher = new Dispatcher();
        HookAwarePolicy policy = new HookAwarePolicy();
        RerouteSuccessStrategy nav = new RerouteSuccessStrategy();
        Robot recoveredBot = new Robot(UUID.fromString("00000000-0000-4000-a000-000000000010"), "RecoveredBot", new Vector2D(1, 1));
        Robot blockingBot = new Robot(UUID.fromString("00000000-0000-4000-a000-000000000001"), "BlockingBot", new Vector2D(2, 1));
        Task task = new Task(42, new Vector2D(2, 2), new Vector2D(2, 2), 1);

        // The blocker waits on the destination tile, forcing repeated same-target conflicts.
        recoveredBot.setNav(nav);
        dispatcher.addTask(task);
        map.addEntity(recoveredBot);
        map.addEntity(blockingBot);

        SimulationEngine engine = new SimulationEngine(map, new Robot[] { recoveredBot, blockingBot }, dispatcher, policy);

        for (int i = 0; i < 5; i++) {
            engine.tick();
        }

        assertEquals(RobotState.MOVING, recoveredBot.getState());
        assertEquals(task, recoveredBot.getCurrentTask());
        assertEquals(0, dispatcher.getPendingTaskCount());
        assertTrue(recoveredBot.hasRerouteAttemptedForCurrentTask());
        assertEquals(new Vector2D(2, 1), recoveredBot.getRerouteAvoidTile());
        assertEquals(1, policy.clearCalls);
        assertEquals(1, nav.resetCalls);

        engine.tick();

        assertEquals(new Vector2D(1, 0), recoveredBot.getPosition());
        assertNull(recoveredBot.getRerouteAvoidTile());
        assertTrue(recoveredBot.hasRerouteAttemptedForCurrentTask());
    }

    @Test
    void secondDeadlockFallsBackToResetAndRequeue() {
        Map map = new Map(3, 3);
        Dispatcher dispatcher = new Dispatcher();
        HookAwarePolicy policy = new HookAwarePolicy();
        RerouteFailureStrategy nav = new RerouteFailureStrategy();
        Robot recoveredBot = new Robot(UUID.fromString("00000000-0000-4000-a000-000000000010"), "RecoveredBot", new Vector2D(1, 1));
        Robot blockingBot = new Robot(UUID.fromString("00000000-0000-4000-a000-000000000001"), "BlockingBot", new Vector2D(2, 1));
        Task task = new Task(43, new Vector2D(2, 2), new Vector2D(2, 2), 1);

        recoveredBot.setNav(nav);
        dispatcher.addTask(task);
        map.addEntity(recoveredBot);
        map.addEntity(blockingBot);

        SimulationEngine engine = new SimulationEngine(map, new Robot[] { recoveredBot, blockingBot }, dispatcher, policy);

        for (int i = 0; i < 10; i++) {
            engine.tick();
        }

        assertEquals(RobotState.IDLE, recoveredBot.getState());
        assertNull(recoveredBot.getCurrentTask());
        assertFalse(recoveredBot.hasRerouteAttemptedForCurrentTask());
        assertNull(recoveredBot.getRerouteAvoidTile());
        assertEquals(1, dispatcher.getPendingTaskCount());
        assertEquals(TaskStatus.PENDING, dispatcher.getAllTasks().get(0).getStatus());
        assertEquals(2, policy.clearCalls);
        assertEquals(2, nav.resetCalls);
    }

    @Test
    void fallingBackImmediatelyWhenTheAvoidTileIsTheTargetRequeuesTheTask() {
        Map map = new Map(3, 3);
        Dispatcher dispatcher = new Dispatcher();
        HookAwarePolicy policy = new HookAwarePolicy();
        TargetTileStrategy nav = new TargetTileStrategy();
        Robot recoveredBot = new Robot(UUID.fromString("00000000-0000-4000-a000-000000000010"), "RecoveredBot", new Vector2D(1, 1));
        Robot blockingBot = new Robot(UUID.fromString("00000000-0000-4000-a000-000000000001"), "BlockingBot", new Vector2D(2, 1));
        Task task = new Task(44, new Vector2D(2, 1), new Vector2D(2, 2), 1);

        recoveredBot.setNav(nav);
        dispatcher.addTask(task);
        map.addEntity(recoveredBot);
        map.addEntity(blockingBot);

        SimulationEngine engine = new SimulationEngine(map, new Robot[] { recoveredBot, blockingBot }, dispatcher, policy);

        for (int i = 0; i < 5; i++) {
            engine.tick();
        }

        assertEquals(RobotState.IDLE, recoveredBot.getState());
        assertNull(recoveredBot.getCurrentTask());
        assertFalse(recoveredBot.hasRerouteAttemptedForCurrentTask());
        assertEquals(1, dispatcher.getPendingTaskCount());
        assertEquals(1, policy.clearCalls);
        assertEquals(1, nav.resetCalls);
    }

    @Test
    void assigningANewTaskResetsTheRerouteBudget() {
        Map map = new Map(3, 3);
        Robot robot = new Robot("budget-bot", new Vector2D(1, 1));
        Task firstTask = new Task(100, new Vector2D(2, 2), new Vector2D(2, 2), 1);
        Task secondTask = new Task(101, new Vector2D(0, 0), new Vector2D(0, 0), 1);

        robot.setNav(new RerouteSuccessStrategy());
        robot.setCurrentTask(firstTask);
        robot.setState(RobotState.MOVING);
        robot.getNextMove(map);

        assertTrue(robot.startDeadlockRerouteAttempt());
        assertTrue(robot.hasRerouteAttemptedForCurrentTask());
        assertNotNull(robot.getRerouteAvoidTile());

        robot.setCurrentTask(secondTask);

        assertFalse(robot.hasRerouteAttemptedForCurrentTask());
        assertNull(robot.getRerouteAvoidTile());
        assertNull(robot.getLastRequestedNextTile());
    }

    private static class RerouteSuccessStrategy implements NavigationStrategy {
        private int resetCalls;

        @Override
        public MoveIntention getNextMove(Robot robot, Map map) {
            Tile fromTile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            Vector2D next = (robot.getRerouteAvoidTile() == null) ? new Vector2D(2, 1) : new Vector2D(1, 0);
            return new MoveIntention(fromTile, map.getTile(next.getX(), next.getY()), robot);
        }

        @Override
        public void reset(Robot robot) {
            resetCalls++;
        }
    }

    private static class RerouteFailureStrategy implements NavigationStrategy {
        private int resetCalls;

        @Override
        public MoveIntention getNextMove(Robot robot, Map map) {
            Tile fromTile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            if (robot.getRerouteAvoidTile() == null) {
                return new MoveIntention(fromTile, map.getTile(2, 1), robot);
            }
            return new MoveIntention(fromTile, fromTile, robot);
        }

        @Override
        public void reset(Robot robot) {
            resetCalls++;
        }
    }

    private static class TargetTileStrategy implements NavigationStrategy {
        private int resetCalls;

        @Override
        public MoveIntention getNextMove(Robot robot, Map map) {
            Tile fromTile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            return new MoveIntention(fromTile, map.getTile(2, 1), robot);
        }

        @Override
        public void reset(Robot robot) {
            resetCalls++;
        }
    }

    private static class HookAwarePolicy implements CoordinationPolicy {
        private int clearCalls;

        @Override
        public MoveIntention[] apply(Map map, MoveIntention[] intentions) {
            return intentions;
        }

        @Override
        public void clearRobotCoordinationState(Robot robot) {
            clearCalls++;
        }
    }

    private static void assertNotNull(Object value) {
        assertTrue(value != null);
    }
}
