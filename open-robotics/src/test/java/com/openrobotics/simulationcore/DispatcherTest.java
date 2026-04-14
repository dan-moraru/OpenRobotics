package com.openrobotics.simulationcore;

import com.openrobotics.AppState;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Dispatcher}.
 *
 */
public class DispatcherTest {

    private Dispatcher dispatcher;

    /**
     * Seed AppState with a dummy SimulationEngine to satisfy logging code
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
     * Creates a fresh Dispatcher before every test.
     */
    @BeforeEach
    public void setUp() {
        dispatcher = new Dispatcher();
        seedAppState();
        Logger.setMode(LoggerMode.NO_OP); // disable logging during tests
    }

    // Helpers
    /**
     * Creates a task.
     * @param id the id of the task
     * @param priority the priority of the task
     * @return the task
     */
    private static Task makeTask(int id, int priority) {
        return new Task(id, new Vector2D(0, 0), new Vector2D(1, 1), priority);
    }

    /**
     * Creates an idle robot.
     * @param name the name of the robot
     * @return the robot
     */
    private static Robot makeIdleRobot(String name) {
        return new Robot(name, new Vector2D(0, 0));
    }

    /**
     * A new Dispatcher has no pending tasks.
     */
    @Test
    public void testInitiallyEmpty() {
        assertFalse(dispatcher.hasPendingTasks());
        assertEquals(0, dispatcher.getPendingTaskCount());
    }

    /**
     * A new Dispatcher starts with zero total tasks added.
     */
    @Test
    public void testInitiallyZeroTotalTasksAdded() {
        assertEquals(0, dispatcher.getTotalTasksAdded());
    }

    /**
     * Adding a single task increases the pending count to 1.
     */
    @Test
    public void testAddTaskIncreasesPendingCount() {
        dispatcher.addTask(makeTask(1, 5));
        assertEquals(1, dispatcher.getPendingTaskCount());
        assertTrue(dispatcher.hasPendingTasks());
    }

    /**
     * addTask() increments the lifetime total-tasks-added counter.
     */
    @Test
    public void testAddTaskIncrementsTotalTasksAdded() {
        dispatcher.addTask(makeTask(1, 5));
        dispatcher.addTask(makeTask(2, 6));

        assertEquals(2, dispatcher.getTotalTasksAdded());
    }

    /**
     * A task's status is set to PENDING when added.
     */
    @Test
    public void testAddTaskSetsPendingStatus() {
        Task task = makeTask(1, 0);
        task.setStatus(TaskStatus.IN_PROGRESS); // intentionally wrong status
        dispatcher.addTask(task);
        assertEquals(TaskStatus.PENDING, task.getStatus());
    }

    /**
     * Adding a null task must throw IllegalArgumentException.
     */
    @Test
    public void testAddNullTaskThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.addTask(null));
    }

    /**
     * Adding a duplicate task (same id) must throw IllegalStateException.
     */
    @Test
    public void testAddDuplicateTaskThrowsError() {
        Task task = makeTask(1, 0);
        dispatcher.addTask(task);
        assertThrows(IllegalStateException.class, () -> dispatcher.addTask(task),
                "Adding the exact same task twice must throw IllegalStateException");
    }

    /**
     * Multiple distinct tasks can be added without error.
     */
    @Test
    public void testAddMultipleDistinctTasks() {
        dispatcher.addTask(makeTask(1, 3));
        dispatcher.addTask(makeTask(2, 1));
        dispatcher.addTask(makeTask(3, 5));
        assertEquals(3, dispatcher.getPendingTaskCount());
    }

    /**
     * addTasks(null) must throw IllegalArgumentException.
     */
    @Test
    public void testAddTasksNullListThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.addTasks(null));
    }

    /**
     * addTasks() with a list of distinct tasks adds them all.
     */
    @Test
    public void testAddTasksBulk() {
        dispatcher.addTasks(List.of(makeTask(1, 1), makeTask(2, 2), makeTask(3, 3)));
        assertEquals(3, dispatcher.getPendingTaskCount());
    }

    /**
     * addTasks() rejects null entries and reports the failing index.
     */
    @Test
    public void testAddTasksIgnoresNullEntries() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.addTasks(Arrays.asList(makeTask(1, 1), null, makeTask(2, 2))));

        assertTrue(ex.getMessage().contains("index 1"));
    }

    /**
     * addTasks() should surface duplicate-task rejection from addTask().
     */
    @Test
    public void testAddTasksDuplicateThrows() {
        Task task = makeTask(1, 1);

        assertThrows(IllegalStateException.class, () -> dispatcher.addTasks(List.of(task, task)));
    }

    /**
     * An empty list passed to addTasks() results in no change.
     */
    @Test
    public void testAddTasksEmptyList() {
        dispatcher.addTasks(List.of());
        assertEquals(0, dispatcher.getPendingTaskCount());
    }

    /**
     * getAllQueuedTasks() returns a snapshot of the current queue.
     */
    @Test
    public void testGetAllTasksReturnsAllEnqueued() {
        Task t1 = makeTask(1, 2);
        Task t2 = makeTask(2, 4);
        dispatcher.addTask(t1);
        dispatcher.addTask(t2);

        List<Task> all = dispatcher.getAllQueuedTasks();
        assertEquals(2, all.size());
        assertTrue(all.contains(t1));
        assertTrue(all.contains(t2));
    }

    /**
     * getAllQueuedTasks() should return tasks in dispatcher priority order.
     */
    @Test
    public void testGetAllTasksReturnsPrioritySortedSnapshot() {
        Task low = makeTask(1, 1);
        Task high = makeTask(2, 10);
        Task medium = makeTask(3, 5);
        dispatcher.addTask(low);
        dispatcher.addTask(high);
        dispatcher.addTask(medium);

        List<Task> all = dispatcher.getAllQueuedTasks();

        assertEquals(List.of(high, medium, low), all);
    }

    /**
     * getAllQueuedTasks() returns a defensive copy; modifying it doesn't affect the dispatcher.
     */
    @Test
    public void testGetAllTasksDefensiveCopy() {
        dispatcher.addTask(makeTask(1, 0));
        List<Task> snapshot = dispatcher.getAllQueuedTasks();
        snapshot.clear();
        assertEquals(1, dispatcher.getPendingTaskCount(),
                "Clearing the snapshot must not affect the internal queue");
    }

    /**
     * assignTasks(null) must throw IllegalArgumentException.
     */
    @Test
    public void testAssignTasksNullArrayThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.assignTasks(null));
    }

    /**
     * assignTasks with an empty array is a no-op and returns 0.
     */
    @Test
    public void testAssignTasksEmptyArrayThrows() {
        assertEquals(0, dispatcher.assignTasks(new Robot[0]));
    }

    /**
     * Returns 0 when there are no pending tasks.
     */
    @Test
    public void testAssignTasksNoTasksReturnsZero() {
        Robot[] robots = { makeIdleRobot("R1") };
        assertEquals(0, dispatcher.assignTasks(robots));
    }

    /**
     * An available robot receives a task and transitions to MOVING.
     */
    @Test
    public void testAssignTasksAvailableRobotGetsTask() {
        Task  task  = makeTask(1, 5);
        Robot robot = makeIdleRobot("R1");
        dispatcher.addTask(task);

        int count = dispatcher.assignTasks(new Robot[]{ robot });

        assertEquals(1,               count);
        assertEquals(task,            robot.getCurrentTask());
        assertEquals(RobotState.MOVING, robot.getState());
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
    }

    /**
     * A non-available robot (MOVING) should not be assigned a task.
     */
    @Test
    public void testAssignTasksSkipsNonAvailableRobot() {
        Robot robot = makeIdleRobot("R1");
        robot.setState(RobotState.MOVING);
        dispatcher.addTask(makeTask(1, 0));

        int count = dispatcher.assignTasks(new Robot[]{ robot });
        assertEquals(0, count, "Non-available robot must not receive a task");
        assertNull(robot.getCurrentTask());
        assertEquals(1, dispatcher.getPendingTaskCount(),
                "Task must remain in queue when no robot could receive it");
    }

    /**
     * Null robot entries are skipped while available robots still receive tasks.
     */
    @Test
    public void testAssignTasksSkipsNullRobotEntries() {
        dispatcher.addTask(makeTask(1, 5));
        Robot robot = makeIdleRobot("R1");

        int count = dispatcher.assignTasks(new Robot[]{ null, robot });

        assertEquals(1, count);
        assertNotNull(robot.getCurrentTask());
    }

    /**
     * Priority ordering: the higher-number-priority task is assigned first.
     */
    @Test
    public void testAssignTasksByPriority() {
        Task lowPriority  = makeTask(1, 1);
        Task highPriority = makeTask(2, 10);
        dispatcher.addTask(lowPriority);
        dispatcher.addTask(highPriority);

        Robot robot = makeIdleRobot("R1");
        dispatcher.assignTasks(new Robot[]{ robot });

        assertSame(highPriority, robot.getCurrentTask(),
                "Task with higher priority number must be assigned first");
    }

    /**
     * Each available robot gets exactly one task.
     */
    @Test
    public void testAssignTasksOnePerRobot() {
        dispatcher.addTask(makeTask(1, 1));
        dispatcher.addTask(makeTask(2, 2));
        dispatcher.addTask(makeTask(3, 3));

        Robot r1 = makeIdleRobot("R1");
        Robot r2 = makeIdleRobot("R2");
        int count = dispatcher.assignTasks(new Robot[]{ r1, r2 });

        assertEquals(2, count);
        assertNotNull(r1.getCurrentTask());
        assertNotNull(r2.getCurrentTask());
        assertEquals(1, dispatcher.getPendingTaskCount(),
                "One task should remain unassigned");
    }

    /**
     * When there are fewer tasks than robots, only as many assignments as tasks are made.
     */
    @Test
    public void testAssignTasksFewerTasksThanRobots() {
        dispatcher.addTask(makeTask(1, 0));

        Robot r1 = makeIdleRobot("R1");
        Robot r2 = makeIdleRobot("R2");
        int count = dispatcher.assignTasks(new Robot[]{ r1, r2 });

        assertEquals(1, count);
        assertFalse(dispatcher.hasPendingTasks());
    }

    /**
     * Once tasks have been assigned, the pending queue shrinks but the total-added counter does not.
     */
    @Test
    public void testAssignTasksDoesNotReduceTotalTasksAdded() {
        dispatcher.addTask(makeTask(1, 1));
        dispatcher.addTask(makeTask(2, 2));

        dispatcher.assignTasks(new Robot[]{ makeIdleRobot("R1") });

        assertEquals(1, dispatcher.getPendingTaskCount());
        assertEquals(2, dispatcher.getTotalTasksAdded());
    }

    /**
     * requeueTask() adds the task back to the queue.
     */
    @Test
    public void testRequeueTask() {
        Task task = makeTask(1, 0);
        dispatcher.addTask(task);

        // Manually simulate consumption by popping via assignment
        Robot robot = makeIdleRobot("R1");
        dispatcher.assignTasks(new Robot[]{ robot });
        assertFalse(dispatcher.hasPendingTasks());

        // Reset the task status so addTask accepts it
        task.setStatus(TaskStatus.PENDING);
        robot.setCurrentTask(null);
        robot.setState(RobotState.IDLE);

        dispatcher.requeueTask(task);
        assertTrue(dispatcher.hasPendingTasks());
        assertEquals(1, dispatcher.getPendingTaskCount());
    }

    /**
     * requeueTask() should not increase total-tasks-added because it is not a newly added task.
     */
    @Test
    public void testRequeueTaskIncrementsTotalTasksAdded() {
        Task task = makeTask(1, 0);
        dispatcher.addTask(task);
        dispatcher.assignTasks(new Robot[]{ makeIdleRobot("R1") });

        task.setStatus(TaskStatus.PENDING);

        dispatcher.requeueTask(task);

        assertEquals(1, dispatcher.getTotalTasksAdded());
    }
}
