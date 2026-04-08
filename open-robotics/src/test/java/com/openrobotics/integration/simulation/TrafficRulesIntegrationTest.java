package com.openrobotics.integration.simulation;

import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TrafficRulesIntegrationTest extends SimulationIntegrationTestSupport {

    @Test
    void trafficRulesKeepsIntersectionExclusiveAcrossTicks() {
        Map map = new Map(4, 4);

        Robot robotA = greedyRobot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "A",
                0,
                1,
                7L
        );
        Robot robotB = greedyRobot(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "B",
                2,
                0,
                7L
        );

        Task taskA = new Task(1, pos(0, 1), pos(3, 1), 1);
        robotA.setCurrentTask(taskA);
        robotA.setHasPickedUp(true);
        robotA.setState(RobotState.MOVING);

        Task taskB = new Task(2, pos(2, 0), pos(2, 3), 1);
        robotB.setCurrentTask(taskB);
        robotB.setHasPickedUp(true);
        robotB.setState(RobotState.MOVING);

        map.addEntity(robotA);
        map.addEntity(robotB);

        TrafficRulesPolicy policy = new TrafficRulesPolicy(Set.of(
                map.getTile(1, 1),
                map.getTile(2, 1)
        ));

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robotA, robotB},
                new Dispatcher(),
                policy
        );

        engine.tick();
        assertEquals(pos(1, 1), robotA.getPosition());
        assertEquals(pos(2, 0), robotB.getPosition());

        engine.tick();
        assertEquals(pos(2, 1), robotA.getPosition());
        assertEquals(pos(2, 0), robotB.getPosition());

        engine.tick();
        assertEquals(pos(3, 1), robotA.getPosition());
        assertEquals(pos(2, 0), robotB.getPosition());
        assertEquals(RobotState.UNLOADING, robotA.getState());

        engine.tick();
        assertEquals(pos(3, 1), robotA.getPosition());
        assertEquals(pos(2, 1), robotB.getPosition());
        assertNull(robotA.getCurrentTask());
        assertSame(taskB, robotB.getCurrentTask());
        assertEquals(4, engine.getTickCounter());
    }
}
