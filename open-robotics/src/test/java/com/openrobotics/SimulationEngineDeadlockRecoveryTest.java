package com.openrobotics;

import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineDeadlockRecoveryTest {

    @Test
    void requeuesTheTaskAndResetsTheRobotAfterFiveStuckTicks() {
        Map map = new Map(1, 1);
        Dispatcher dispatcher = new Dispatcher();
        Robot robot = new Robot("stuck-bot", new Vector2D(0, 0));
        Task task = new Task(42, new Vector2D(1, 0), new Vector2D(1, 0), 1);

        // The engine assigns the task on the first tick, then the robot stays in place while MOVING.
        dispatcher.addTask(task);
        map.addEntity(robot);

        SimulationEngine engine = new SimulationEngine(map, new Robot[] { robot }, dispatcher, CoordinationPolicy.noOp());

        for (int i = 0; i < 5; i++) {
            engine.tick();
        }

        assertEquals(RobotState.IDLE, robot.getState());
        assertNull(robot.getCurrentTask());
        assertEquals(0, robot.getStuckTicks());
        assertEquals(1, dispatcher.getPendingTaskCount());
        assertEquals(1, dispatcher.getTotalTasksAdded());
        assertEquals(TaskStatus.PENDING, dispatcher.getAllTasks().get(0).getStatus());
    }

    @Test
    void invokesNavigationAndCoordinationRecoveryHooks() {
        Map map = new Map(1, 1);
        Dispatcher dispatcher = new Dispatcher();
        ResetAwareStrategy nav = new ResetAwareStrategy();
        RecoverAwarePolicy policy = new RecoverAwarePolicy();
        Robot robot = new Robot("recoverable-bot", new Vector2D(0, 0));

        // The engine assigns the task on the first tick, then the custom strategy keeps waiting.
        robot.setNav(nav);
        dispatcher.addTask(new Task(7, new Vector2D(1, 0), new Vector2D(1, 0), 1));
        map.addEntity(robot);

        SimulationEngine engine = new SimulationEngine(map, new Robot[] { robot }, dispatcher, policy);

        for (int i = 0; i < 5; i++) {
            engine.tick();
        }

        assertTrue(nav.wasReset);
        assertTrue(policy.wasNotified);
    }

    private static class ResetAwareStrategy implements NavigationStrategy {
        private boolean wasReset;

        @Override
        public MoveIntention getNextMove(Robot robot, Map map) {
            Tile tile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            return new MoveIntention(tile, tile, robot);
        }

        @Override
        public void reset(Robot robot) {
            // The test only needs to know that recovery reached the navigation layer.
            wasReset = true;
        }
    }

    private static class RecoverAwarePolicy implements CoordinationPolicy {
        private boolean wasNotified;

        @Override
        public MoveIntention[] apply(Map map, MoveIntention[] intentions) {
            return intentions;
        }

        @Override
        public void onRobotRecovered(Robot robot) {
            // The test only needs to know that recovery reached the coordination layer.
            wasNotified = true;
        }
    }
}
