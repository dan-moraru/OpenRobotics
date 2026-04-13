package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RackTest {

    @Test
    void newRackDefaultsToRandomAssignment() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        assertFalse(rack.isManualDropoffAssignment(),
                "New racks must default to random (non-manual) mode");
    }

    @Test
    void manualAssignmentFlagIsPersistent() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        rack.setManualDropoffAssignment(true);
        assertTrue(rack.isManualDropoffAssignment());
        rack.setManualDropoffAssignment(false);
        assertFalse(rack.isManualDropoffAssignment());
    }

    @Test
    void togglingManualOffPreservesValidDropoffIds() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        java.util.UUID a = java.util.UUID.randomUUID();
        rack.getValidDropoffIds().add(a);
        rack.setManualDropoffAssignment(true);
        rack.setManualDropoffAssignment(false);
        assertEquals(java.util.List.of(a), rack.getValidDropoffIds(),
                "Toggling manual mode must not clear the existing pool");
    }

    @Test
    void boxCountSetterEnforcesMinimumOfOne() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        rack.setBoxCount(0);
        assertEquals(1, rack.getBoxCount());
        rack.setBoxCount(-5);
        assertEquals(1, rack.getBoxCount());
    }
}
