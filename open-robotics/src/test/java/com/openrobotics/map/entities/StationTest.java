package com.openrobotics.map.entities;

import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChargingStation} and {@link DeliveryStation}.
 *
 * <p>Both classes extend the abstract {@code Station} base, so the tests
 * exercise the inherited {@code isBusy} flag as well as the concrete
 * constructors and identity fields.
 */
public class StationTest {

    /**
     * A new ChargingStation should have the correct name and position.
     */
    @Test
    public void testChargingStationNameAndPosition() {
        ChargingStation cs = new ChargingStation("CS-1", new Vector2D(3, 4));
        assertEquals("CS-1", cs.getName());
        assertEquals(new Vector2D(3, 4), cs.getPosition());
    }

    /**
     * A new ChargingStation must start in the non-busy state.
     */
    @Test
    public void testChargingStationInitiallyNotBusy() {
        ChargingStation cs = new ChargingStation("CS-2", new Vector2D(0, 0));
        assertFalse(cs.isBusy(), "ChargingStation must start as not busy");
    }

    /**
     * The explicit-UUID constructor for ChargingStation should store the given UUID.
     */
    @Test
    public void testChargingStationExplicitUuid() {
        UUID id = UUID.randomUUID();
        ChargingStation cs = new ChargingStation(id, "CS-UUID", new Vector2D(1, 1));
        assertEquals(id, cs.getId());
    }

    /**
     * The UUID-loading constructor should still initialize busy=false.
     */
    @Test
    public void testChargingStationExplicitUuidStartsNotBusy() {
        ChargingStation cs = new ChargingStation(UUID.randomUUID(), "CS-UUID", new Vector2D(1, 1));
        assertFalse(cs.isBusy());
    }

    /**
     * setBusy(true) on a ChargingStation should be reflected by isBusy().
     */
    @Test
    public void testChargingStationSetBusyTrue() {
        ChargingStation cs = new ChargingStation("CS-3", new Vector2D(2, 2));
        cs.setBusy(true);
        assertTrue(cs.isBusy());
    }

    /**
     * setBusy(false) after setBusy(true) should clear the busy flag.
     */
    @Test
    public void testChargingStationSetBusyFalse() {
        ChargingStation cs = new ChargingStation("CS-4", new Vector2D(5, 5));
        cs.setBusy(true);
        cs.setBusy(false);
        assertFalse(cs.isBusy());
    }

    /**
     * Two distinct ChargingStation instances should have different auto-generated UUIDs.
     */
    @Test
    public void testChargingStationUniqueIds() {
        ChargingStation a = new ChargingStation("A", new Vector2D(0, 0));
        ChargingStation b = new ChargingStation("B", new Vector2D(0, 0));
        assertNotEquals(a.getId(), b.getId());
    }

    /**
     * ChargingStation inherits from the abstract Station base and MapEntity.
     */
    @Test
    public void testChargingStationInheritanceHierarchy() {
        ChargingStation cs = new ChargingStation("CS", new Vector2D(0, 0));
        assertInstanceOf(com.openrobotics.map.entities.station.Station.class, cs);
        assertInstanceOf(com.openrobotics.map.MapEntity.class, cs);
    }

    /**
     * Inherited mutators from MapEntity should work on ChargingStation.
     */
    @Test
    public void testChargingStationInheritedSetters() {
        ChargingStation cs = new ChargingStation("CS", new Vector2D(0, 0));

        cs.setName("CS-Renamed");
        cs.setPosition(new Vector2D(4, 5));

        assertEquals("CS-Renamed", cs.getName());
        assertEquals(new Vector2D(4, 5), cs.getPosition());
        assertFalse(cs.isBusy(), "Renaming or moving a station must not change busy state");
    }

    /**
     * A new DeliveryStation should have the correct name and position.
     */
    @Test
    public void testDeliveryStationNameAndPosition() {
        DeliveryStation ds = new DeliveryStation("DS-1", new Vector2D(8, 6));
        assertEquals("DS-1", ds.getName());
        assertEquals(new Vector2D(8, 6), ds.getPosition());
    }

    /**
     * A new DeliveryStation must start in the non-busy state.
     */
    @Test
    public void testDeliveryStationInitiallyNotBusy() {
        DeliveryStation ds = new DeliveryStation("DS-2", new Vector2D(0, 0));
        assertFalse(ds.isBusy(), "DeliveryStation must start as not busy");
    }

    /**
     * The explicit-UUID constructor for DeliveryStation should store the given UUID.
     */
    @Test
    public void testDeliveryStationExplicitUuid() {
        UUID id = UUID.randomUUID();
        DeliveryStation ds = new DeliveryStation(id, "DS-UUID", new Vector2D(3, 7));
        assertEquals(id, ds.getId());
    }

    /**
     * The UUID-loading constructor should still initialize busy=false.
     */
    @Test
    public void testDeliveryStationExplicitUuidStartsNotBusy() {
        DeliveryStation ds = new DeliveryStation(UUID.randomUUID(), "DS-UUID", new Vector2D(3, 7));
        assertFalse(ds.isBusy());
    }

    /**
     * setBusy(true) on a DeliveryStation should be reflected by isBusy().
     */
    @Test
    public void testDeliveryStationSetBusyTrue() {
        DeliveryStation ds = new DeliveryStation("DS-3", new Vector2D(1, 1));
        ds.setBusy(true);
        assertTrue(ds.isBusy());
    }

    /**
     * setBusy(false) after setBusy(true) should clear the busy flag.
     */
    @Test
    public void testDeliveryStationSetBusyFalse() {
        DeliveryStation ds = new DeliveryStation("DS-4", new Vector2D(2, 2));
        ds.setBusy(true);
        ds.setBusy(false);
        assertFalse(ds.isBusy());
    }

    /**
     * DeliveryStation inherits from the abstract Station base and MapEntity.
     */
    @Test
    public void testDeliveryStationInheritanceHierarchy() {
        DeliveryStation ds = new DeliveryStation("DS", new Vector2D(0, 0));
        assertInstanceOf(com.openrobotics.map.entities.station.Station.class, ds);
        assertInstanceOf(com.openrobotics.map.MapEntity.class, ds);
    }

    /**
     * Inherited mutators from MapEntity should work on DeliveryStation.
     */
    @Test
    public void testDeliveryStationInheritedSetters() {
        DeliveryStation ds = new DeliveryStation("DS", new Vector2D(1, 1));

        ds.setName("DS-Renamed");
        ds.setPosition(new Vector2D(7, 8));

        assertEquals("DS-Renamed", ds.getName());
        assertEquals(new Vector2D(7, 8), ds.getPosition());
        assertFalse(ds.isBusy(), "Renaming or moving a station must not change busy state");
    }

    /**
     * Busy state on a ChargingStation does not affect a DeliveryStation.
     */
    @Test
    public void testBusyStateIsIsolatedPerInstance() {
        ChargingStation cs = new ChargingStation("CS", new Vector2D(0, 0));
        DeliveryStation ds = new DeliveryStation("DS", new Vector2D(0, 0));

        cs.setBusy(true);
        assertFalse(ds.isBusy(), "Busy state of one station must not affect another instance");
    }
}
