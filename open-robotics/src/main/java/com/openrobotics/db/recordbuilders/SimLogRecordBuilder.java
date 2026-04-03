package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.logging.eventtypes.RobotEvent;

import java.util.UUID;

public class SimLogRecordBuilder {
    private SimLogRecord record;

    /**
     * Creates a new SimLogRecord and initializes the record with the given simulation run ID,
     * tick, robot ID, and robot position (x, y) for general simulation log information.
     * @param simulationRunId the UUID of the simulation run
     * @param tick the tick of the simulation log record
     * @param robotId the ID of the robot associated with the simulation log record
     * @param x the x coordinate of the robot's position for the simulation log record
     * @param y the y coordinate of the robot's position for the simulation log record
     */
    public SimLogRecordBuilder(UUID simulationRunId, int tick, UUID robotId, int x, int y) {
        this.record = new SimLogRecord();
        record.setRunId(simulationRunId);
        record.setTick(tick);
        record.setRobotId(robotId);
        record.setX(x);
        record.setY(y);
    }

    /**
     * Sets the event type of the simulation log record to MOVE_EXECUTED.
     * @return the built SimLogRecord for a move execution event
     */
    public SimLogRecord buildMoveExecutionRecord() {
        record.setEventType(RobotEvent.MOVE_EXECUTED.toString());
        return record;
    }

    /**
     * Sets the event type of the simulation log record to COLLISION and
     * sets the details of the collision event.
     * @param details the details of the collision event to be included in the simulation log record
     * @return the built SimLogRecord for a collision event with the provided details
     */
    public SimLogRecord buildCollisionRecord(String details) {
        record.setEventType(RobotEvent.COLLISION.toString());
        record.setDetails(details);
        return record;
    }

    /**
     * Sets the event type of the simulation log record to NEAR_MISS and
     * sets the details of the near miss event.
     * @param details the details of the near miss event to be included in the simulation log record
     * @return the built SimLogRecord for a near miss event with the provided details
     */
    public SimLogRecord buildNearMissRecord(String details) {
        record.setEventType(RobotEvent.NEAR_MISS.toString());
        record.setDetails(details);
        return record;
    }

    /**
     * Sets the event type of the simulation log record to BATTERY_DEATH.
     * @return the built SimLogRecord for a battery death event
     */
     public SimLogRecord buildBatteryDeathRecord() {
        record.setEventType(RobotEvent.BATTERY_DEATH.toString());
        return record;
    }
}
