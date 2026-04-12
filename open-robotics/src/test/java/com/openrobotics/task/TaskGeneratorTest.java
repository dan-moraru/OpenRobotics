package com.openrobotics.task;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.DeliveryStation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskGeneratorTest {

    @Test
    void generatesTasksFromRacksToDeliveryStations() {
        Map map = new Map(10, 10);
        Rack rack = new Rack("rack_1", new Vector2D(2, 2));
        rack.setBoxCount(2);
        DeliveryStation ds = new DeliveryStation("delivery_1", new Vector2D(8, 5));
        map.addEntity(rack);
        map.addEntity(ds);

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 10, 42L);

        assertEquals(2, tasks.size());
        for (Task t : tasks) {
            assertEquals(2, (int) t.getPickupLocation().getX());
            assertEquals(2, (int) t.getPickupLocation().getY());
            assertEquals(8, (int) t.getDropoffLocation().getX());
            assertEquals(5, (int) t.getDropoffLocation().getY());
        }
    }

    @Test
    void returnsEmptyWhenNoDeliveryStations() {
        Map map = new Map(10, 10);
        map.addEntity(new Rack("rack_1", new Vector2D(2, 2)));

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 10, 42L);

        assertTrue(tasks.isEmpty());
    }

    @Test
    void respectsMaxCount() {
        Map map = new Map(10, 10);
        Rack rack = new Rack("rack_1", new Vector2D(2, 2));
        rack.setBoxCount(10);
        map.addEntity(rack);
        map.addEntity(new DeliveryStation("ds_1", new Vector2D(8, 5)));

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 3, 42L);

        assertEquals(3, tasks.size());
    }

    @Test
    void respectsValidDropoffIds() {
        com.openrobotics.map.Map map = new com.openrobotics.map.Map(10, 10);
        Rack rack = new Rack("rack_1", new Vector2D(2, 2));
        rack.setBoxCount(5);
        DeliveryStation ds1 = new DeliveryStation("ds_1", new Vector2D(8, 5));
        DeliveryStation ds2 = new DeliveryStation("ds_2", new Vector2D(9, 5));
        map.addEntity(rack);
        map.addEntity(ds1);
        map.addEntity(ds2);
        // Only allow ds1 as a valid dropoff
        rack.setValidDropoffIds(java.util.List.of(ds1.getId()));

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 10, 42L);

        assertEquals(5, tasks.size());
        for (Task t : tasks) {
            assertEquals(8, (int) t.getDropoffLocation().getX(), "should only drop off at ds1");
            assertEquals(5, (int) t.getDropoffLocation().getY(), "should only drop off at ds1");
        }
    }
}
