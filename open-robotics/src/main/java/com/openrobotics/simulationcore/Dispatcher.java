package com.openrobotics.simulationcore;

import com.openrobotics.AppState;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.eventtypes.TaskEvent;
import com.openrobotics.db.recordbuilders.WorkloadTaskRecordBuilder;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;

import java.util.*;

/** Manages a priority queue of tasks and assigns them to available robots each tick. */
public class Dispatcher {
    private final List<Task> lifetimeTasks; // stores all tasks ever added to this dispatcher
    private final PriorityQueue<Task> taskQueue;
    private int totalTasksAdded;

    public Dispatcher() {
        this.lifetimeTasks = new ArrayList<>();
        this.taskQueue = new PriorityQueue<>();
        this.totalTasksAdded = 0;
    }

    /**
     * Adds a task to the pending queue and the lifetime task list.
     *
     * @param task the task to enqueue; must not be {@code null} or already present
     */
    public void addTask(Task task) {
        enqueueTask(task, true);
        lifetimeTasks.add(task);
    }

    private void enqueueTask(Task task, boolean countTowardsTotal) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        } else if (taskQueue.contains(task)) {
            throw new IllegalStateException("Task is already in the task queue");
        }

        task.setStatus(TaskStatus.PENDING);
        taskQueue.add(task);
        if (countTowardsTotal) {
            totalTasksAdded++;
        }
    }

    /**
     * Adds tasks to the queue in bulk.
     *
     * @param tasks the list of tasks to enqueue; must not be {@code null} and must contain no {@code null} entries
     */
    public void addTasks(List<Task> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("Tasks list cannot be null");
        }

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (task == null) {
                throw new IllegalArgumentException("null task in tasks list at index " + i);
            }
            addTask(task);
        }
    }

    /**
     * Assigns at most one pending task per available robot; logs each assignment.
     *
     * @param robots the array of robots to assign tasks to; must not be {@code null}
     * @return the number of assignments made during this call
     */
    public int assignTasks(Robot[] robots) {
        if (robots == null) {
            throw new IllegalArgumentException("Robots array cannot be null");
        } else if (robots.length == 0 || taskQueue.isEmpty()) {
            return 0; // there are no tasks available, no assignments are made
        }

        int currentTick = AppState.getEngine().getTickCounter(); // getting the current simulation tick from global app state
        int assignmentCount = 0;

        for (Robot robot : robots) {
            if (robot == null) {
                continue;
            }

            if (taskQueue.isEmpty()) {
                break;
            }

            if (robot.isAvailable()) {
                Task task = taskQueue.peek();
                if (task == null) {
                    break;
                }
                try {
                    robot.setCurrentTask(task);
                    robot.setState(RobotState.MOVING);
                    task.setStatus(TaskStatus.IN_PROGRESS);

                    taskQueue.poll();
                    assignmentCount++;

                    WorkloadTaskRecordBuilder recordBuilder = new WorkloadTaskRecordBuilder(AppState.getEngine().getRunId(), task);
                    WorkloadTaskRecord record = recordBuilder.buildTaskAssignmentRecord(currentTick, robot.getId());
                    Logger.logTaskEvent(TaskEvent.TASK_ASSIGNED, record);
                } catch (RuntimeException ex) {
                    System.err.println("[Dispatcher] Failed to assign task " + task.getId()
                            + " to robot " + robot.getId() + ": " + ex.getMessage());
                }
            }
        }

        return assignmentCount;
    }

    /**
     * Requeues a task without incrementing the total count; used for deadlock recovery.
     *
     * @param task the task to requeue; must not be {@code null} or already present in the queue
     */
    public void requeueTask(Task task) {
        enqueueTask(task, false);
    }

    public boolean hasPendingTasks() {
        return !taskQueue.isEmpty();
    }

    public int getPendingTaskCount() {
        return taskQueue.size();
    }

    /**
     * Returns all currently queued (pending) tasks sorted by natural task ordering.
     *
     * @return sorted list of pending tasks
     */
    public List<Task> getAllQueuedTasks() {
        List<Task> tasks = new ArrayList<>(taskQueue);
        tasks.sort(Task::compareTo);
        return tasks;
    }

    /**
     * Returns all tasks ever added to this dispatcher, including completed and in-progress ones.
     *
     * @return a snapshot copy of the lifetime task list
     */
    public List<Task> getLifetimeTasks() {
        return new ArrayList<>(lifetimeTasks);
    }

    /**
     * Returns the total number of tasks ever added to this dispatcher.
     * Used to distinguish "no tasks were configured" from "all tasks completed".
     *
     * @return the total number of tasks added since construction
     */
    public int getTotalTasksAdded() {
        return totalTasksAdded;
    }
}
