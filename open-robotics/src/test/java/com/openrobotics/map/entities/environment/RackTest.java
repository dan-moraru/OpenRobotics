package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Rack} state-management behavior.
 *
 * <p>This suite verifies defaults and invariants around manual dropoff assignment configuration,
 * persistence of valid dropoff identifiers, and minimum constraints on rack box count.</p>
 */
class RackTest {

    /**
     * Verifies newly constructed racks default to random (non-manual) dropoff assignment mode.
     */
    @Test
    void newRackDefaultsToRandomAssignment() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        assertFalse(rack.isManualDropoffAssignment(),
                "New racks must default to random (non-manual) mode");
    }

    /**
     * Verifies manual assignment flag can be toggled on/off and retains assigned value.
     */
    @Test
    void manualAssignmentFlagIsPersistent() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        rack.setManualDropoffAssignment(true);
        assertTrue(rack.isManualDropoffAssignment());
        rack.setManualDropoffAssignment(false);
        assertFalse(rack.isManualDropoffAssignment());
    }

    /**
     * Verifies disabling manual mode does not clear previously configured valid dropoff IDs.
     */
    @Test
    void togglingManualOffPreservesValidDropoffIds() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        UUID a = UUID.randomUUID();
        rack.getValidDropoffIds().add(a);
        rack.setManualDropoffAssignment(true);
        rack.setManualDropoffAssignment(false);
        assertEquals(List.of(a), rack.getValidDropoffIds(),
                "Toggling manual mode must not clear the existing pool");
    }

    /**
     * Verifies box count setter clamps invalid low values to minimum supported count of zero.
     */
    @Test
    void boxCountSetterEnforcesMinimumOfZero() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        rack.setBoxCount(0);
        assertEquals(0, rack.getBoxCount());
        rack.setBoxCount(-5);
        assertEquals(0, rack.getBoxCount());
    }
}
