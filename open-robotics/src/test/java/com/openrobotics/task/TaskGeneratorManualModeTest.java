package com.openrobotics.task;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.DeliveryStation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TaskGenerator} behavior when rack manual dropoff mode is enabled/disabled.
 *
 * <p>This suite verifies that random mode ignores rack-specific dropoff restrictions, while manual
 * mode enforces filtered round-robin assignment over resolvable, non-null dropoff station IDs.</p>
 */
class TaskGeneratorManualModeTest {

    /**
     * Creates a deterministic empty map fixture with stable dimensions for task-generation tests.
     */
    private Map buildMap() {
        return new Map(UUID.randomUUID(), 10, 10);
    }

    /**
     * Verifies random mode ignores {@code validDropoffIds} and samples from all stations.
     */
    @Test
    void randomModeIgnoresValidDropoffIds() {
        // In random (non-manual) mode, validDropoffIds is ignored — all stations are valid.
        Map m = buildMap();
        DeliveryStation a = new DeliveryStation("A", new Vector2D(5, 0));
        DeliveryStation b = new DeliveryStation("B", new Vector2D(6, 0));
        m.addEntity(a); m.addEntity(b);
        Rack rack = new Rack("rack", new Vector2D(1, 1));
        rack.setBoxCount(20);
        rack.getValidDropoffIds().add(a.getId()); // restrict to A only in data...
        rack.setManualDropoffAssignment(false);    // ...but manual mode is OFF so ignored
        m.addEntity(rack);

        List<Task> tasks = new TaskGenerator(m, 42L).generateTasks(20);

        boolean usedB = tasks.stream().anyMatch(t ->
            t.getDropoffLocation().equals(b.getPosition()));
        assertTrue(usedB, "Random mode must draw from all stations, not just validDropoffIds");
    }

    /**
     * Verifies manual mode performs ordered round-robin over the non-null, resolvable station pool.
     */
    @Test
    void manualModeUsesRoundRobinOverNonNullPool() {
        Map m = buildMap();
        DeliveryStation a = new DeliveryStation("A", new Vector2D(5, 0));
        DeliveryStation b = new DeliveryStation("B", new Vector2D(6, 0));
        m.addEntity(a); m.addEntity(b);
        Rack rack = new Rack("rack", new Vector2D(1, 1));
        rack.setBoxCount(5);
        rack.getValidDropoffIds().add(a.getId());
        rack.getValidDropoffIds().add(null);       // null slot must be skipped
        rack.getValidDropoffIds().add(b.getId());
        rack.setManualDropoffAssignment(true);
        m.addEntity(rack);

        List<Task> tasks = new TaskGenerator(m, 42L).generateTasks(100);

        assertEquals(5, tasks.size());
        List<Vector2D> drops = tasks.stream()
            .map(Task::getDropoffLocation).toList();
        assertEquals(List.of(
            a.getPosition(), b.getPosition(),
            a.getPosition(), b.getPosition(),
            a.getPosition()), drops,
            "Round-robin must cycle through non-null pool in order");
    }

    /**
     * Verifies manual mode drops unresolvable station IDs and uses only mapped stations.
     */
    @Test
    void manualModeFiltersUnresolvableIds() {
        Map m = buildMap();
        DeliveryStation a = new DeliveryStation("A", new Vector2D(5, 0));
        m.addEntity(a);
        Rack rack = new Rack("rack", new Vector2D(1, 1));
        rack.setBoxCount(3);
        rack.getValidDropoffIds().add(UUID.randomUUID()); // not in map
        rack.getValidDropoffIds().add(a.getId());
        rack.setManualDropoffAssignment(true);
        m.addEntity(rack);

        List<Task> tasks = new TaskGenerator(m, 42L).generateTasks(100);

        assertEquals(3, tasks.size());
        for (Task t : tasks) {
            assertEquals(a.getPosition(), t.getDropoffLocation());
        }
    }
}
