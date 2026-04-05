package com.openrobotics.integration.simulation;

import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.map.Map;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SimulationChargingIntegrationTest extends SimulationIntegrationTestSupport {

    @Test
    void lowBatteryRobotDivertsToChargerBeforeResumingTask() {
        Map map = new Map(6, 2);
        Robot robot = greedyRobot("ChargeBot", 0, 0, 2024L);
        robot.setBattery(10.0f);
        map.addEntity(robot);
        map.addEntity(new ChargingStation("Charger", pos(0, 1)));

        Dispatcher dispatcher = new Dispatcher();
        Task task = new Task(1, pos(4, 0), pos(5, 0), 1);
        dispatcher.addTask(task);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robot},
                dispatcher,
                CoordinationPolicy.noOp()
        );

        engine.tick();

        assertSame(task, robot.getCurrentTask());
        assertEquals(pos(0, 1), robot.getPosition());
        assertEquals(RobotState.MOVING, robot.getState());
        assertEquals(9.0f, robot.getBattery(), 0.001f);

        engine.tick();

        assertEquals(pos(0, 1), robot.getPosition());
        assertEquals(RobotState.CHARGING, robot.getState());
        assertEquals(14.0f, robot.getBattery(), 0.001f);

        runTicksUntil(
                engine,
                () -> robot.getBattery() >= 100.0f && robot.getState() == RobotState.MOVING,
                30,
                "Robot never finished charging and resumed moving"
        );

        assertEquals(pos(0, 1), robot.getPosition());
        assertSame(task, robot.getCurrentTask());
        assertEquals(100.0f, robot.getBattery(), 0.001f);
        assertEquals(RobotState.MOVING, robot.getState());

        runTicksUntil(
                engine,
                () -> !robot.getPosition().equals(pos(0, 1)),
                5,
                "Robot never left the charging station after recharging"
        );

        assertNotEquals(pos(0, 1), robot.getPosition());
        assertSame(task, robot.getCurrentTask());
    }
}
