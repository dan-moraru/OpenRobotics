package com.openrobotics.logging.eventtypes;

/** Lifecycle events for a workload task; used to categorize {@code run_workload_tasks} table updates. */
public enum TaskEvent {
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_COMPLETED,
}
