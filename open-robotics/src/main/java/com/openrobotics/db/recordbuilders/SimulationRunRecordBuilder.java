package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.simulationcore.SimulationEngine;

import java.sql.Timestamp;
import java.time.Instant;

/** Builds {@link SimulationRunRecord} instances for simulation lifecycle events. */
public class SimulationRunRecordBuilder {
    private SimulationRunRecord record;

    /**
     * Creates a builder pre-filled from the engine's run ID, map, robot count, and coordination policy.
     *
     * @param engine the simulation engine to extract run metadata from
     */
    public SimulationRunRecordBuilder(SimulationEngine engine) {
        this.record = new SimulationRunRecord();
        record.setId(engine.getRunId());
        record.setMapId(engine.getMap().getMapid());
        record.setRobotCount(engine.getRobots().length);
        record.setCoordinationPolicy(engine.getCoordinationPolicy());
    }

    // TODO: set all simulation run fields appropriately (probably do this in the constructor)
    /**
     * Builds the simulation start record; sets {@code startedAt} to now and status to {@code RUNNING}.
     *
     * @return the populated simulation run record
     */
    public SimulationRunRecord buildSimulationStartRecord() {
        record.setStartedAt(Timestamp.from(Instant.now()));
        record.setStatus("RUNNING");
        return record;
    }

    /**
     * Builds the simulation complete record; sets {@code finishedAt} to now and status to {@code COMPLETED}.
     *
     * @return the populated simulation run record
     */
    public SimulationRunRecord buildSimulationCompleteRecord() {
        record.setFinishedAt(Timestamp.from(Instant.now()));
        record.setStatus("COMPLETED");
        return record;
    }
}
