package com.openrobotics.simulationcore;

import com.openrobotics.robot.Robot;
import com.openrobotics.task.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Dispatcher {
    // Pending tasks waiting for assignment this tick (FIFO)
    private final List<Task> pendingTasks;

    public Dispatcher() {
        this.pendingTasks = new ArrayList<>();
    }

    // Adds a task to the pending queue if it is not null and not already present
    public void addTask(Task task) {
        if (task == null) {
            return;
        }

        for (Task existing : pendingTasks) {
            if (existing.getId() == task.getId()) {
                return;
            }
        }

        task.setStatus("PENDING");
        pendingTasks.add(task);
    }

    // Also added a bulk-add used when adding a batch of tasks
    public void addTasks(List<Task> tasks) {
        if (tasks == null) {
            return;
        }
        for (Task task : tasks) {
            addTask(task);
        }
    }

    // Assign at most one task per available robot
    // Returns the number of assignments performed for this tick
    public int assignTasks(List<Robot> robots) {
        if (robots == null || robots.isEmpty() || pendingTasks.isEmpty()) {
            return 0;
        }

        // task order:
        // 1) higher priority first
        // 2) lower task id first as tie-break
        pendingTasks.sort(Comparator
                .comparingInt(Task::getPriority).reversed()
                .thenComparingInt(Task::getId));

        // robot order:
        // 1) robot name
        // 2) Id string as tie-break
        List<Robot> robotOrder = new ArrayList<>(robots);
        robotOrder.sort(Comparator
                .comparing(Robot::getName, Comparator.nullsFirst(String::compareTo))
                .thenComparing(robot -> robot.getId().toString()));

        int assignmentCount = 0;
        for (Robot robot : robotOrder) {
            if (pendingTasks.isEmpty()) {
                break;
            }
            if (robot == null || !robot.isAvailable()) {
                continue;
            }

            // Remove one task from queue and assign it to this robot
            Task task = pendingTasks.remove(0);
            task.setStatus("IN_PROGRESS");
            robot.setCurrentTask(task);
            assignmentCount++;
        }

        return assignmentCount;
    }

    // Requeues a task (for future deadlock/failure recovery)
    public void requeueTask(Task task) {
        addTask(task);
    }

    public boolean hasPendingTasks() {
        return !pendingTasks.isEmpty();
    }

    public int getPendingTaskCount() {
        return pendingTasks.size();
    }

    public List<Task> getAllTasks() {
        return new ArrayList<>(pendingTasks);
    }
}
