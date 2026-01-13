package com.openrobotics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RobotTest {

    @Test
    public void testRobotInitialStatus() {
        Robot robot = new Robot("0", 0, 0);
        assertEquals("Idle", robot.getStatus(), "New robots should start in Idle state.");
    }
}