package com.openrobotics.robot;

import com.openrobotics.map.Vector2D;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Core unit tests for baseline {@link Robot} behavior.
 *
 * <p>This suite validates constructor defaults, inherited entity identity/position fields, battery
 * consumption invariants, charging-threshold checks, availability rules, and explicit state/status
 * transitions.</p>
 */
public class RobotTest {

    /**
     * Verifies a newly constructed robot reports the default IDLE status label.
     */
    @Test
    public void testRobotInitialStatus() {
        Robot robot = new Robot("BOT-001", new Vector2D(0, 0));
        assertEquals("IDLE", robot.getStatus(), "New robots should start in Idle state.");
    }

    /**
     * Verifies robot instances expose inherited {@code MapEntity}-level fields correctly.
     */
    @Test
    public void testRobotInheritsMapEntity() {
        Robot robot = new Robot("BOT-002", new Vector2D(5, 10));
        // position comes from mapentity parent
        assertEquals(new Vector2D(5, 10), robot.getPosition());
        assertEquals("BOT-002", robot.getName());
        assertNotNull(robot.getId());
    }

    /**
     * Verifies battery value decreases by the consumed energy amount.
     */
    @Test
    public void testRobotBattery() {
        Robot robot = new Robot("BOT-003", new Vector2D(0, 0));
        assertEquals(100.0f, robot.getBattery());
        robot.consumeEnergy(25.5f);
        assertEquals(74.5f, robot.getBattery(), 0.01f);
    }

    /**
     * Verifies battery consumption is clamped at zero (never negative).
     */
    @Test
    public void testRobotBatteryCannotGoNegative() {
        Robot robot = new Robot("BOT-004", new Vector2D(0, 0));
        robot.consumeEnergy(200.0f);
        assertEquals(0.0f, robot.getBattery());
    }

    /**
     * Verifies charging-threshold check flips once battery drops below threshold.
     */
    @Test
    public void testRobotNeedsCharging() {
        Robot robot = new Robot("BOT-005", new Vector2D(0, 0));
        assertFalse(robot.needsCharging(20.0f));
        robot.consumeEnergy(85.0f);
        assertTrue(robot.needsCharging(20.0f));
    }

    /**
     * Verifies availability reflects idle/no-task baseline and becomes false while moving.
     */
    @Test
    public void testRobotAvailability() {
        Robot robot = new Robot("BOT-006", new Vector2D(0, 0));
        // initially idle with no task — should be available
        assertTrue(robot.isAvailable());
        robot.setState(RobotState.MOVING);
        assertFalse(robot.isAvailable());
    }

    /**
     * Verifies explicit state changes are reflected by both enum state and string status accessors.
     */
    @Test
    public void testRobotStateTransitions() {
        Robot robot = new Robot("BOT-007", new Vector2D(0, 0));
        assertEquals(RobotState.IDLE, robot.getState());
        robot.setState(RobotState.CHARGING);
        assertEquals(RobotState.CHARGING, robot.getState());
        assertEquals("CHARGING", robot.getStatus());
    }
}
