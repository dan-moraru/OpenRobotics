package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.simulationcore.SimulationEngine;

import java.sql.Timestamp;
import java.time.Instant;

public class SimulationRunRecordBuilder {
    private SimulationRunRecord record;

    /**
     * Creates a new SimulationRunRecord and initializes the record with the given simulation engine
     * for general simulation information.
     * @param engine The simulation engine to initialize the record with.
     */
    public SimulationRunRecordBuilder(SimulationEngine engine) {
        this.record = new SimulationRunRecord();
        record.setId(engine.getRunId());
        record.setMapId(engine.getMap().getMapid());
        record.setRobotCount(engine.getRobots().length);
        record.setCoordinationPolicy(engine.getCoordinationPolicy());
    }

    /**
     * Builds a SimulationRunRecord for the start of a simulation run by setting the started at timestamp and status to 'RUNNING'.
     * @return a SimulationRunRecord for the start of a simulation run
     */
    // TODO: Set all simulation run fields appropriately (probably do this in the constructor)
    public SimulationRunRecord buildSimulationStartRecord() {
        record.setStartedAt(Timestamp.from(Instant.now()));
        record.setStatus("RUNNING");
        return record;
    }

    /**
     * Builds a SimulationRunRecord for the completion of a simulation run by setting the finished at timestamp and status to 'COMPLETED'.
     * @return a SimulationRunRecord for the completion of a simulation run
     */
    public SimulationRunRecord buildSimulationCompleteRecord() {
        record.setFinishedAt(Timestamp.from(Instant.now()));
        record.setStatus("COMPLETED");
        return record;
    }
}
