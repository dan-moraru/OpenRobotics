package com.openrobotics;

import com.openrobotics.map.Map;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.Vector2D;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskGeneratorTest {

    @Test
    void generatesCorrectNumberOfTasks() {
        Map map = new Map(10, 10);
        map.addEntity(new ChargingStation("Station1", new Vector2D(1, 1)));
        map.addEntity(new ChargingStation("Station2", new Vector2D(8, 8)));

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 5, 42L);

        assertEquals(5, tasks.size());
    }

    @Test
    void generatesUniquePickupDropoffLocations() {
        Map map = new Map(10, 10);
        map.addEntity(new ChargingStation("Station1", new Vector2D(1, 1)));

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 3, 42L);

        for (Task task : tasks) {
            assertFalse(task.getPickupLocation().equals(task.getDropoffLocation()),
                "Pickup and dropoff should be different");
        }
    }

    @Test
    void respectsRandomSeed() {
        Map map = new Map(20, 20);
        map.addEntity(new ChargingStation("Station1", new Vector2D(1, 1)));
        map.addEntity(new ChargingStation("Station2", new Vector2D(18, 18)));

        List<Task> tasks1 = TaskGenerator.generateRandomTasks(map, 5, 12345L);
        List<Task> tasks2 = TaskGenerator.generateRandomTasks(map, 5, 12345L);

        assertEquals(tasks1.size(), tasks2.size());
        for (int i = 0; i < tasks1.size(); i++) {
            assertEquals(tasks1.get(i).getPickupLocation(), tasks2.get(i).getPickupLocation());
            assertEquals(tasks1.get(i).getDropoffLocation(), tasks2.get(i).getDropoffLocation());
        }
    }

    @Test
    void generatesTasksWithValidLocations() {
        Map map = new Map(10, 10);
        map.addEntity(new ChargingStation("Station1", new Vector2D(1, 1)));

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 3, 42L);

        for (Task task : tasks) {
            Vector2D pickup = task.getPickupLocation();
            Vector2D dropoff = task.getDropoffLocation();

            assertTrue(pickup.getX() >= 0 && pickup.getX() < map.getWidth());
            assertTrue(pickup.getY() >= 0 && pickup.getY() < map.getHeight());
            assertTrue(dropoff.getX() >= 0 && dropoff.getX() < map.getWidth());
            assertTrue(dropoff.getY() >= 0 && dropoff.getY() < map.getHeight());
        }
    }

    @Test
    void handlesMapWithNoValidLocations() {
        Map map = new Map(2, 2);
        map.addEntity(new Obstacle("Wall00", new Vector2D(0, 0)));
        map.addEntity(new Obstacle("Wall01", new Vector2D(0, 1)));
        map.addEntity(new Obstacle("Wall10", new Vector2D(1, 0)));
        map.addEntity(new Obstacle("Wall11", new Vector2D(1, 1)));

        List<Task> tasks = TaskGenerator.generateRandomTasks(map, 5, 42L);

        assertTrue(tasks.isEmpty());
    }
}
