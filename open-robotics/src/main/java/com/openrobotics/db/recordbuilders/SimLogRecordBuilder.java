package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.logging.eventtypes.RobotEvent;

import java.util.UUID;

/** Builds {@link SimLogRecord} instances for each robot event type. */
public class SimLogRecordBuilder {
    private SimLogRecord record;

    /**
     * Creates a builder pre-filled with the run ID, tick, robot ID, and tile position.
     *
     * @param simulationRunId the UUID of the simulation run
     * @param tick the simulation tick at which the event occurred
     * @param robotId the UUID of the robot involved in the event
     * @param x the tile x-coordinate of the event
     * @param y the tile y-coordinate of the event
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
     * Builds a {@code MOVE_EXECUTED} log record.
     *
     * @return the populated sim log record
     */
    public SimLogRecord buildMoveExecutionRecord() {
        record.setEventType(RobotEvent.MOVE_EXECUTED.toString());
        return record;
    }

    /**
     * Builds a {@code COLLISION} log record.
     *
     * @param details collision details as a JSON string
     * @return the populated sim log record
     */
    public SimLogRecord buildCollisionRecord(String details) {
        record.setEventType(RobotEvent.COLLISION.toString());
        record.setDetails(details);
        return record;
    }

    /**
     * Builds a {@code NEAR_MISS} log record.
     *
     * @param details near miss details as a JSON string
     * @return the populated sim log record
     */
    public SimLogRecord buildNearMissRecord(String details) {
        record.setEventType(RobotEvent.NEAR_MISS.toString());
        record.setDetails(details);
        return record;
    }

    /**
     * Builds a {@code BATTERY_DEATH} log record.
     *
     * @return the populated sim log record
     */
    public SimLogRecord buildBatteryDeathRecord() {
        record.setEventType(RobotEvent.BATTERY_DEATH.toString());
        return record;
    }

    /**
     * Builds a {@code CHARGE_START} log record.
     *
     * @param details JSON with structure: {'batteryLevel': int}
     * @return the populated sim log record
     */
    public SimLogRecord buildChargeStartRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.CHARGE_START.toString());
        return record;
    }

    /**
     * Builds a {@code CHARGE_END} log record.
     *
     * @param details JSON with structure: {'batteryLevel': int}
     * @return the populated sim log record
     */
    public SimLogRecord buildChargeEndRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.CHARGE_END.toString());
        return record;
    }

    /**
     * Builds a {@code DEADLOCK_DETECTED} log record.
     *
     * @param details JSON with structure: {'stuckTicks': int}
     * @return the populated sim log record
     */
    public SimLogRecord buildDeadlockDetectionRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.DEADLOCK_DETECTED.toString());
        return record;
    }

    /**
     * Builds a {@code DEADLOCK_RESOLVED} log record.
     *
     * @param details JSON with structure: {'resolutionMethod': String}
     * @return the populated sim log record
     */
    public SimLogRecord buildDeadlockResolutionRecord(String details) {
        record.setDetails(details);
        record.setEventType(RobotEvent.DEADLOCK_RESOLVED.toString());
        return record;
    }
}
