package com.openrobotics.simulationcore;

import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;

import java.util.*;

/**
 * The Dispatcher manages a queue of tasks for robots to complete ordered by priority
 */
public class Dispatcher {
    private final PriorityQueue<Task> taskQueue;

    /**
     * Creates a priority queue for tasks
     */
    public Dispatcher() {
        this.taskQueue = new PriorityQueue<>();
    }

    /**
     * Adds a task to the pending queue if it is not null and not already present
     * @param task the task being added to the task queue
     */
    public void addTask(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        } else if (taskQueue.contains(task)) {
            throw new IllegalStateException("Task is already in the task queue");
        }

        // Set task status as pending and add to queue
        task.setStatus(TaskStatus.PENDING);
        taskQueue.add(task);
    }

    /**
     * Adds tasks to the task queue in bulk
     * @param tasks a list of tasks being added to the task queue
     */
    public void addTasks(List<Task> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("Tasks list cannot be null");
        }

        // Adding tasks to task queue
        for (Task task : tasks) {
            if (task != null) {
                addTask(task);
            }
        }
    }

    /**
     * Assigns at most one task per available robot
     * @param robots a list of all the robots in the warehouse
     * @return the number of successful assignments performed
     */
    public int assignTasks(Robot[] robots) {
        if (robots == null) {
            throw new IllegalArgumentException("Robots array cannot be null");
        } else if (robots.length == 0) {
            throw new IllegalArgumentException("Robots array cannot be empty");
        } else if (taskQueue.isEmpty()) {
            return 0; // there are no available tasks, 0 task assignments made
        }

        int assignmentCount = 0;

        // Assigning tasks to available robots
        for (Robot robot : robots) {
            if (taskQueue.isEmpty()) { // There are no more tasks left to assign
                break;
            }

            if (robot == null) {
                continue;
            }

            if (robot.isAvailable()) { // robot is available for task assignment
                // Assign a task to the robot
                Task task = taskQueue.poll();
                robot.setCurrentTask(task);

                // Update robot state and task status
                robot.setState(RobotState.MOVING);
                task.setStatus(TaskStatus.IN_PROGRESS);

                assignmentCount++;
            }
        }

        return assignmentCount;
    }

    /**
     * Requeues a task (for future deadlock/failure recovery)
     * @param task the task to be requeued
     */
    public void requeueTask(Task task) {
        addTask(task);
    }

    /**
     * Returns true if the task queue is not empty
     * @return true if the task queue is not empty
     */
    public boolean hasPendingTasks() {
        return !taskQueue.isEmpty();
    }

    /**
     * Returns the number of pending tasks in the task queue
     * @return the number of tasks in the task queue
     */
    public int getPendingTaskCount() {
        return taskQueue.size();
    }

    /**
     * Returns a list of all the tasks in the task queue
     * @return a list containing all the tasks in the task queue
     */
    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>(taskQueue);
        tasks.sort(Task::compareTo);
        return tasks;
    }
}
