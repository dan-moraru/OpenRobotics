package com.openrobotics.task;

import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.DeliveryStation;

import java.util.*;

public class TaskGenerator {
    private final com.openrobotics.map.Map map;
    private final Random random;

    public TaskGenerator(com.openrobotics.map.Map map, long seed) {
        this.map = map;
        this.random = new Random(seed);
    }

    /**
     * Generates tasks by pairing rack positions (pickups) with valid delivery
     * station positions (dropoffs). Respects per-rack boxCount and validDropoffIds.
     * If a rack has no validDropoffIds set, all delivery stations are valid dropoffs.
     */
    public List<Task> generateTasks(int maxCount) {
        List<Task> tasks = new ArrayList<>();

        // Collect all delivery stations indexed by UUID
        Map<UUID, DeliveryStation> stationById = new LinkedHashMap<>();
        List<DeliveryStation> allStations = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof DeliveryStation ds) {
                stationById.put(ds.getId(), ds);
                allStations.add(ds);
            }
        }

        if (allStations.isEmpty()) return tasks;

        // Collect racks
        List<Rack> racks = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof Rack r) racks.add(r);
        }
        if (racks.isEmpty()) return tasks;

        // Shuffle racks for variety
        Collections.shuffle(racks, random);

        int taskId = 1;
        outer:
        for (Rack rack : racks) {
            // Resolve valid dropoff stations for this rack
            List<DeliveryStation> validStations;
            if (rack.getValidDropoffIds() == null || rack.getValidDropoffIds().isEmpty()) {
                validStations = allStations;
            } else {
                validStations = new ArrayList<>();
                for (UUID id : rack.getValidDropoffIds()) {
                    DeliveryStation ds = stationById.get(id);
                    if (ds != null) validStations.add(ds);
                }
                if (validStations.isEmpty()) validStations = allStations;
            }

            // Generate up to boxCount tasks from this rack
            for (int i = 0; i < rack.getBoxCount(); i++) {
                if (tasks.size() >= maxCount) break outer;
                DeliveryStation station = validStations.get(random.nextInt(validStations.size()));
                int priority = random.nextInt(3) + 1;
                tasks.add(new Task(taskId++,
                    new Vector2D(rack.getPosition().getX(), rack.getPosition().getY()),
                    new Vector2D(station.getPosition().getX(), station.getPosition().getY()),
                    priority));
            }
        }
        return tasks;
    }

    public static List<Task> generateRandomTasks(com.openrobotics.map.Map map, int count, long seed) {
        return new TaskGenerator(map, seed).generateTasks(count);
    }
}
