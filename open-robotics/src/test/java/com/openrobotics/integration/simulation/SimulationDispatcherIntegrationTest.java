package com.openrobotics.integration.simulation;

import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Integration test for dispatcher-driven task assignment across simulation ticks.
 *
 * <p>This suite verifies priority ordering and wave-based assignment behavior when multiple robots
 * consume queued tasks over time.</p>
 */
public class SimulationDispatcherIntegrationTest extends SimulationIntegrationTestSupport {

    /**
     * Verifies dispatcher assigns highest-priority tasks first to available robots, then feeds
     * remaining lower-priority work in a later wave after completions.
     *
     * <p>The assertions track task status transitions, pending-queue counts, and robot completion
     * counters to ensure assignment and progression remain consistent across ticks.</p>
     */
    @Test
    void dispatcherFeedsHighestPriorityTasksAcrossMultipleCompletionWaves() {
        Map map = new Map(6, 2);

        Robot robotA = greedyRobot("A", 0, 0, 1L);
        Robot robotB = greedyRobot("B", 0, 1, 1L);
        map.addEntity(robotA);
        map.addEntity(robotB);

        Task highPriority = new Task(1, pos(1, 0), pos(2, 0), 10);
        Task mediumPriority = new Task(2, pos(1, 1), pos(2, 1), 5);
        Task lowPriority = new Task(3, pos(3, 0), pos(4, 0), 1);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(lowPriority);
        dispatcher.addTask(highPriority);
        dispatcher.addTask(mediumPriority);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robotA, robotB},
                dispatcher,
                CoordinationPolicy.noOp()
        );

        engine.tick();

        assertSame(highPriority, robotA.getCurrentTask());
        assertSame(mediumPriority, robotB.getCurrentTask());
        assertEquals(TaskStatus.IN_PROGRESS, highPriority.getStatus());
        assertEquals(TaskStatus.IN_PROGRESS, mediumPriority.getStatus());
        assertEquals(TaskStatus.PENDING, lowPriority.getStatus());
        assertEquals(1, dispatcher.getPendingTaskCount());

        runTicks(engine, 3);

        assertEquals(TaskStatus.COMPLETED, highPriority.getStatus());
        assertEquals(TaskStatus.COMPLETED, mediumPriority.getStatus());
        assertEquals(TaskStatus.PENDING, lowPriority.getStatus());
        assertEquals(1, dispatcher.getPendingTaskCount());
        assertEquals(1, robotA.getTasksCompleted());
        assertEquals(1, robotB.getTasksCompleted());

        engine.tick();

        assertSame(lowPriority, robotA.getCurrentTask());
        assertEquals(TaskStatus.IN_PROGRESS, lowPriority.getStatus());
        assertEquals(0, dispatcher.getPendingTaskCount());
    }
}
