package com.openrobotics.integration.simulation;

import com.openrobotics.AppState;
import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for core single-robot task execution flow.
 *
 * <p>This suite verifies that a task progresses through dispatcher assignment, robot pickup/dropoff
 * movement, completion bookkeeping, and stable post-completion engine behavior.</p>
 */
public class SimulationTaskFlowIntegrationTest extends SimulationIntegrationTestSupport {

    /**
     * Seeds {@link AppState} with a minimal engine so logging-dependent code paths are satisfied
     * during integration tests.
     */
    private void seedAppState() {
        SimulationEngine dummyEngine = new SimulationEngine(
                new Map(1, 1),
                new Robot[0],
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );

        AppState.setEngine(dummyEngine);
    }

    /**
     * Initializes per-test state and disables logger side effects for deterministic assertions.
     */
    @BeforeEach
    public void setUp() {
        seedAppState();
        Logger.setMode(LoggerMode.NO_OP); // disable logging during tests
    }

    /**
     * Verifies a single robot completes one task end-to-end and then remains stable in idle state.
     *
     * <p>Asserts include task status, robot position/state/metrics, dispatcher depletion, and that
     * an extra tick after completion does not advance simulation time.</p>
     */
    @Test
    void singleRobotTaskCompletesThroughFullEnginePipeline() {
        Map map = new Map(5, 1);
        Robot robot = greedyRobot("R1", 0, 0, 123L);
        map.addEntity(robot);

        Dispatcher dispatcher = new Dispatcher();
        Task task = new Task(1, pos(1, 0), pos(2, 0), 10);
        dispatcher.addTask(task);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robot},
                dispatcher,
                CoordinationPolicy.noOp()
        );

        runTicks(engine, 4);

        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals(pos(2, 0), robot.getPosition());
        assertEquals(RobotState.IDLE, robot.getState());
        assertTrue(robot.isAvailable());
        assertNull(robot.getCurrentTask());
        assertEquals(1, robot.getTasksCompleted());
        assertEquals(2, robot.getTotalDistanceMoved());
        assertEquals(2.0f, robot.getTotalEnergyConsumed(), 0.001f);
        assertEquals(0, dispatcher.getPendingTaskCount());
        assertEquals(4, engine.getTickCounter());

        engine.tick();

        assertEquals(4, engine.getTickCounter());
    }
}
