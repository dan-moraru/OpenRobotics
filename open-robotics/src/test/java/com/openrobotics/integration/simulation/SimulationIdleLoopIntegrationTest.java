package com.openrobotics.integration.simulation;

import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimulationIdleLoopIntegrationTest extends SimulationIntegrationTestSupport {

    @Test
    void noConfiguredTasksKeepsSimulationTickingAndRobotIdle() {
        Map map = new Map(3, 3);
        Robot robot = greedyRobot("IdleBot", 1, 1, 99L);
        map.addEntity(robot);

        Dispatcher dispatcher = new Dispatcher();
        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robot},
                dispatcher,
                CoordinationPolicy.noOp()
        );

        runTicks(engine, 3);

        assertEquals(pos(1, 1), robot.getPosition());
        assertEquals(RobotState.IDLE, robot.getState());
        assertNull(robot.getCurrentTask());
        assertTrue(robot.isAvailable());
        assertEquals(3, robot.getTotalIdleTicks());
        assertEquals(0, dispatcher.getTotalTasksAdded());
        assertEquals(3, engine.getTickCounter());
    }
}
