package com.openrobotics.integration.simulation;

import com.openrobotics.AppState;
import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for temporal spacing behavior under {@link ReservationKPolicy}.
 *
 * <p>This scenario validates that a trailing robot yields to a lead robot in a one-tile corridor
 * when the lead robot has reserved a forward window of tiles.</p>
 */
public class ReservationKIntegrationTest extends SimulationIntegrationTestSupport {

    /**
     * Verifies Reservation-K enforces a wait for the trailing robot until the lead robot advances
     * beyond the reserved horizon.
     *
     * <p>The test uses deterministic UUID ordering because policy internals process robots in UUID
     * order; random IDs would make movement ordering and assertions flaky.</p>
     */
    @Test
    void reservationKMakesTrailingRobotWaitForLeadRobotsWindow() {
        Map map = new Map(5, 1);

        // ReservationKPolicy orders robots by UUID; random IDs make test order flaky.
        Robot leadRobot = greedyRobot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Lead", 1, 0, 51L);
        Task leadTask = new Task(1, pos(1, 0), pos(4, 0), 1);
        leadRobot.setCurrentTask(leadTask);
        leadRobot.setHasPickedUp(true);
        leadRobot.setState(RobotState.MOVING);

        Robot trailingRobot = greedyRobot(
                UUID.fromString("00000000-0000-0000-0000-000000000002"), "Trail", 0, 0, 51L);
        Task trailingTask = new Task(2, pos(0, 0), pos(3, 0), 1);
        trailingRobot.setCurrentTask(trailingTask);
        trailingRobot.setHasPickedUp(true);
        trailingRobot.setState(RobotState.MOVING);

        map.addEntity(leadRobot);
        map.addEntity(trailingRobot);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(trailingTask); // adding task so ticking doesn't fail

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{leadRobot, trailingRobot},
                dispatcher,
                new ReservationKPolicy(2)
        );

        // Setting global state for logging
        AppState.setEngine(engine);
        Logger.setMode(LoggerMode.NO_OP);

        engine.tick();
        assertEquals(pos(2, 0), leadRobot.getPosition());
        assertEquals(pos(0, 0), trailingRobot.getPosition());

        engine.tick();
        assertEquals(pos(3, 0), leadRobot.getPosition());
        assertEquals(pos(1, 0), trailingRobot.getPosition());

        engine.tick();
        assertEquals(pos(4, 0), leadRobot.getPosition());
        assertEquals(pos(2, 0), trailingRobot.getPosition());
    }
}
