package com.openrobotics.task;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.DeliveryStation;

import java.util.*;

/** generates Task objects by pairing rack pickup positions with delivery station dropoff positions */
public class TaskGenerator {
    private final Map map;
    private final Random random;
    private int nextTaskId = 1;

    public TaskGenerator(Map map, long seed) {
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

        java.util.Map<UUID, DeliveryStation> stationById = new LinkedHashMap<>();
        List<DeliveryStation> allStations = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof DeliveryStation ds) {
                stationById.put(ds.getId(), ds);
                allStations.add(ds);
            }
        }

        if (allStations.isEmpty()) return tasks;

        List<Rack> racks = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof Rack r) racks.add(r);
        }
        if (racks.isEmpty()) return tasks;

        // Shuffle racks for variety
        Collections.shuffle(racks, random);

        outer:
        for (Rack rack : racks) {
            // Resolve valid dropoff stations for this rack
            List<DeliveryStation> validStations;
            if (rack.getValidDropoffIds() == null || rack.getValidDropoffIds().isEmpty()) {
                validStations = new ArrayList<>(allStations);
            } else {
                validStations = new ArrayList<>();
                for (UUID id : rack.getValidDropoffIds()) {
                    DeliveryStation ds = stationById.get(id);
                    if (ds != null) validStations.add(ds);
                }
                if (validStations.isEmpty()) validStations = new ArrayList<>(allStations);
            }

            // skip racks with no accessible adjacent tile (e.g. surrounded by walls)
            if (!map.hasTraversableAdjacentTile(rack.getPosition())) {
                continue;
            }

            // generate up to boxCount tasks from this rack
            for (int i = 0; i < rack.getBoxCount(); i++) {
                if (tasks.size() >= maxCount) break outer;
                DeliveryStation station = validStations.get(random.nextInt(validStations.size()));
                int priority = random.nextInt(3) + 1;
                tasks.add(new Task(nextTaskId++,
                    new Vector2D(rack.getPosition().getX(), rack.getPosition().getY()),
                    new Vector2D(station.getPosition().getX(), station.getPosition().getY()),
                    priority));
            }
        }
        return tasks;
    }

    /** convenience factory; constructs a TaskGenerator with the given seed and generates up to {@code count} tasks */
    public static List<Task> generateRandomTasks(Map map, int count, long seed) {
        return new TaskGenerator(map, seed).generateTasks(count);
    }
}
