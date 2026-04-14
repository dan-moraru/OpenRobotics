package com.openrobotics.logging.eventtypes;

/** lifecycle events for a workload task; used to categorize run_workload_task table updates */
public enum TaskEvent {
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_COMPLETED,
}
