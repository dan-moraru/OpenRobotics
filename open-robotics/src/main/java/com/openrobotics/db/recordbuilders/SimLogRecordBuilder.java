package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.logging.eventtypes.RobotEvent;

import java.util.UUID;

/** builds SimLogRecord instances for each robot event type */
public class SimLogRecordBuilder {
    private SimLogRecord record;

    /** initializes a SimLogRecord with the run ID, tick, robot ID, and position */
    public SimLogRecordBuilder(UUID simulationRunId, int tick, UUID robotId, int x, int y) {
        this.record = new SimLogRecord();
        record.setRunId(simulationRunId);
        record.setTick(tick);
        record.setRobotId(robotId);
        record.setX(x);
        record.setY(y);
    }

    /** builds a MOVE_EXECUTED log record */
    public SimLogRecord buildMoveExecutionRecord() {
        record.setEventType(RobotEvent.MOVE_EXECUTED.toString());
        return record;
    }

    /**
     * builds a COLLISION log record.
     *
     * @param details collision details JSON
     */
    public SimLogRecord buildCollisionRecord(String details) {
        record.setEventType(RobotEvent.COLLISION.toString());
        record.setDetails(details);
        return record;
    }

    /**
     * builds a NEAR_MISS log record.
     *
     * @param details near miss details JSON
     */
    public SimLogRecord buildNearMissRecord(String details) {
        record.setEventType(RobotEvent.NEAR_MISS.toString());
        record.setDetails(details);
        return record;
    }

    /** builds a BATTERY_DEATH log record */
    public SimLogRecord buildBatteryDeathRecord() {
        record.setEventType(RobotEvent.BATTERY_DEATH.toString());
        return record;
    }

    /**
     * builds a CHARGE_START log record.
     *
     * @param details JSON with structure: {'batteryLevel': int}
     */
    public SimLogRecord buildChargeStartRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.CHARGE_START.toString());
        return record;
    }

    /**
     * builds a CHARGE_END log record.
     *
     * @param details JSON with structure: {'batteryLevel': int}
     */
    public SimLogRecord buildChargeEndRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.CHARGE_END.toString());
        return record;
    }

    /**
     * builds a DEADLOCK_DETECTED log record.
     *
     * @param details JSON with structure: {'stuckTicks': int}
     */
    public SimLogRecord buildDeadlockDetectionRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.DEADLOCK_DETECTED.toString());
        return record;
    }

    /**
     * builds a DEADLOCK_RESOLVED log record.
     *
     * @param details JSON with structure: {'resolutionMethod': String}
     */
    public SimLogRecord buildDeadlockResolutionRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.DEADLOCK_RESOLVED.toString());
        return record;
    }
}
