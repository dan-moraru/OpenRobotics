package com.openrobotics.common;

import com.openrobotics.map.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Logger}.
 *
 * Verifies that the event list starts empty, that log entries are appended
 * in order, that each entry contains the expected field values, and that
 * {@code getEvents()} returns the live list (not a snapshot).
 */
public class LoggerTest {

    private Logger logger;

    /**
     * Creates a fresh Logger before every test.
     */
    @BeforeEach
    public void setUp() {
        logger = new Logger();
    }

    /**
     * A new Logger should have no events.
     */
    @Test
    public void testInitiallyEmpty() {
        assertTrue(logger.getEvents().isEmpty(), "New Logger must have no events");
    }

    /**
     * After one logEvent() call the list must contain exactly one entry.
     */
    @Test
    public void testSingleEventAdded() {
        logger.logEvent(1, 0, "MOVE", new Vector2D(3, 4), "moved right");
        assertEquals(1, logger.getEvents().size());
    }

    /**
     * The logged entry must contain the tick number.
     */
    @Test
    public void testEntryContainsTick() {
        logger.logEvent(7, 0, "MOVE", new Vector2D(0, 0), "");
        assertTrue(logger.getEvents().get(0).contains("7"),
                "Log entry must contain the tick value");
    }

    /**
     * The logged entry must contain the robot id.
     */
    @Test
    public void testEntryContainsRobotId() {
        logger.logEvent(1, 42, "TASK", new Vector2D(0, 0), "");
        assertTrue(logger.getEvents().get(0).contains("42"),
                "Log entry must contain the robot id");
    }

    /**
     * The logged entry must contain the event type.
     */
    @Test
    public void testEntryContainsEventType() {
        logger.logEvent(1, 0, "CHARGING", new Vector2D(0, 0), "");
        assertTrue(logger.getEvents().get(0).contains("CHARGING"),
                "Log entry must contain the event type");
    }

    /**
     * The logged entry must include the position string.
     */
    @Test
    public void testEntryContainsPosition() {
        logger.logEvent(1, 0, "IDLE", new Vector2D(5, 9), "details");
        String entry = logger.getEvents().get(0);
        assertTrue(entry.contains("5") && entry.contains("9"),
                "Log entry must contain the position coordinates");
    }

    /**
     * The logged entry must include the details string.
     */
    @Test
    public void testEntryContainsDetails() {
        logger.logEvent(1, 0, "EVENT", new Vector2D(0, 0), "task_completed");
        assertTrue(logger.getEvents().get(0).contains("task_completed"),
                "Log entry must contain the details string");
    }

    /**
     * The log entry format should match the current key=value layout exactly.
     */
    @Test
    public void testExactEntryFormat() {
        logger.logEvent(12, 7, "MOVE", new Vector2D(3, 4), "ok");

        assertEquals("tick=12 robot=7 event=MOVE pos=(3, 4) details=ok", logger.getEvents().get(0));
    }

    /**
     * Multiple logEvent() calls should result in the correct number of entries.
     */
    @Test
    public void testMultipleEventsCount() {
        logger.logEvent(1, 0, "A", new Vector2D(0, 0), "");
        logger.logEvent(2, 1, "B", new Vector2D(1, 1), "");
        logger.logEvent(3, 2, "C", new Vector2D(2, 2), "");
        assertEquals(3, logger.getEvents().size());
    }

    /**
     * Events must be stored in the order they were logged.
     */
    @Test
    public void testEventOrder() {
        logger.logEvent(1, 0, "FIRST",  new Vector2D(0, 0), "");
        logger.logEvent(2, 0, "SECOND", new Vector2D(0, 0), "");
        List<String> events = logger.getEvents();
        assertTrue(events.get(0).contains("FIRST"),  "First logged event must be at index 0");
        assertTrue(events.get(1).contains("SECOND"), "Second logged event must be at index 1");
    }

    /**
     * After obtaining a reference via getEvents(), a subsequent logEvent() must
     * be visible through the same reference.
     */
    @Test
    public void testGetEventsIsLiveList() {
        List<String> events = logger.getEvents();
        logger.logEvent(5, 3, "LIVE", new Vector2D(0, 0), "");
        assertEquals(1, events.size(),
                "getEvents() should return the live list, not a snapshot");
    }

    /**
     * Mutating the returned list should affect the logger because it exposes the backing list.
     */
    @Test
    public void testGetEventsReturnsMutableBackingList() {
        List<String> events = logger.getEvents();

        events.add("manually-added");

        assertEquals(1, logger.getEvents().size());
        assertEquals("manually-added", logger.getEvents().get(0));
    }

    /**
     * Null field values are currently accepted and rendered as the string "null".
     */
    @Test
    public void testNullFieldsAreRenderedAsNullStrings() {
        logger.logEvent(1, 2, null, null, null);

        assertEquals("tick=1 robot=2 event=null pos=null details=null", logger.getEvents().get(0));
    }

    /**
     * Negative numeric values are stored verbatim in the log entry.
     */
    @Test
    public void testNegativeTickAndRobotIdAreStoredVerbatim() {
        logger.logEvent(-5, -9, "EVENT", new Vector2D(0, 0), "details");

        assertTrue(logger.getEvents().get(0).contains("tick=-5"));
        assertTrue(logger.getEvents().get(0).contains("robot=-9"));
    }
}
