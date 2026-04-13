package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.simulationcore.SimulationEngine;

import java.sql.Timestamp;
import java.time.Instant;

/** builds SimulationRunRecord instances for simulation lifecycle events */
public class SimulationRunRecordBuilder {
    private SimulationRunRecord record;

    /** initializes a SimulationRunRecord from the engine's run ID, map, robot count, and coordination policy */
    public SimulationRunRecordBuilder(SimulationEngine engine) {
        this.record = new SimulationRunRecord();
        record.setId(engine.getRunId());
        record.setMapId(engine.getMap().getMapid());
        record.setRobotCount(engine.getRobots().length);
        record.setCoordinationPolicy(engine.getCoordinationPolicy());
    }

    // TODO: set all simulation run fields appropriately (probably do this in the constructor)
    /** builds the simulation start record; sets startedAt to now and status to RUNNING */
    public SimulationRunRecord buildSimulationStartRecord() {
        record.setStartedAt(Timestamp.from(Instant.now()));
        record.setStatus("RUNNING");
        return record;
    }

    /** builds the simulation complete record; sets finishedAt to now and status to COMPLETED */
    public SimulationRunRecord buildSimulationCompleteRecord() {
        record.setFinishedAt(Timestamp.from(Instant.now()));
        record.setStatus("COMPLETED");
        return record;
    }
}
