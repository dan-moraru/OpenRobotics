package com.openrobotics.integration.simulation;

import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LongRunningSimulationRegressionTest extends SimulationIntegrationTestSupport {

    @Test
    void multiple_robots_complete_a_longer_queue_without_ever_sharing_a_tile() {
        Map map = new Map(6, 3);
        Robot robotA = greedyRobot("A", 0, 0, 5L);
        Robot robotB = greedyRobot("B", 0, 2, 5L);
        map.addEntity(robotA);
        map.addEntity(robotB);

        Task task1 = new Task(1, pos(1, 0), pos(5, 0), 10);
        Task task2 = new Task(2, pos(1, 2), pos(5, 2), 10);
        Task task3 = new Task(3, pos(4, 0), pos(0, 0), 5);
        Task task4 = new Task(4, pos(4, 2), pos(0, 2), 5);
        List<Task> tasks = List.of(task1, task2, task3, task4);

        Dispatcher dispatcher = new Dispatcher();
        tasks.forEach(dispatcher::addTask);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robotA, robotB},
                dispatcher,
                CoordinationPolicy.noOp()
        );

        for (int i = 0; i < 40; i++) {
            int before = engine.getTickCounter();
            engine.tick();
            assertDistinctAndInBounds(map, robotA, robotB);
            if (engine.getTickCounter() == before) {
                break;
            }
        }

        tasks.forEach(task -> assertEquals(TaskStatus.COMPLETED, task.getStatus()));
        assertEquals(2, robotA.getTasksCompleted());
        assertEquals(2, robotB.getTasksCompleted());
        assertEquals(0, dispatcher.getPendingTaskCount());
        assertNull(robotA.getCurrentTask());
        assertNull(robotB.getCurrentTask());
        assertTrue(robotA.isAvailable());
        assertTrue(robotB.isAvailable());
    }

    @Test
    void long_running_sequence_with_charging_eventually_completes_all_tasks() {
        Map map = new Map(7, 2);
        Robot robot = greedyRobot("BatteryBot", 0, 0, 15L);
        robot.setBattery(10.0f);
        map.addEntity(robot);
        map.addEntity(new ChargingStation("Charge-1", pos(0, 1)));

        Task task1 = new Task(1, pos(3, 0), pos(5, 0), 2);
        Task task2 = new Task(2, pos(4, 0), pos(6, 0), 1);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(task1);
        dispatcher.addTask(task2);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robot},
                dispatcher,
                CoordinationPolicy.noOp()
        );

        runTicksUntil(
                engine,
                () -> robot.getTasksCompleted() == 2 && robot.isAvailable(),
                80,
                "Robot never completed the long-running charged task sequence"
        );

        assertEquals(TaskStatus.COMPLETED, task1.getStatus());
        assertEquals(TaskStatus.COMPLETED, task2.getStatus());
        assertTrue(robot.getTotalChargingTicks() > 0);
        assertTrue(robot.getBattery() > 0.0f);
        assertNull(robot.getCurrentTask());
        assertTrue(map.getTile(robot.getPosition().getX(), robot.getPosition().getY()) != null);

        int completedTick = engine.getTickCounter();
        engine.tick();
        assertEquals(completedTick, engine.getTickCounter());
    }

    private void assertDistinctAndInBounds(Map map, Robot... robots) {
        Set<Vector2D> positions = new HashSet<>();
        for (Robot robot : robots) {
            assertTrue(map.getTile(robot.getPosition().getX(), robot.getPosition().getY()) != null);
            assertTrue(positions.add(robot.getPosition()));
        }
    }
}
