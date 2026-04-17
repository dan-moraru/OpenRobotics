package com.openrobotics.task;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.DeliveryStation;

import java.util.*;

/** Generates {@link Task} objects by pairing rack pickup positions with delivery station dropoff positions. */
public class TaskGenerator {
    private final Map map;
    private final Random random;
    private int nextTaskId = 1;

    /**
     * Creates a TaskGenerator for the given map.
     *
     * @param map the warehouse map to generate tasks for
     * @param seed RNG seed for reproducible task generation
     */
    public TaskGenerator(Map map, long seed) {
        this.map = map;
        this.random = new Random(seed);
    }

    /**
     * Generates tasks by pairing rack positions (pickups) with valid delivery station positions (dropoffs).
     * Respects per-rack {@code boxCount} and {@code validDropoffIds}.
     * If a rack has no {@code validDropoffIds} set, all delivery stations are valid dropoffs.
     *
     * @param maxCount the maximum number of tasks to generate
     * @return list of generated tasks, at most {@code maxCount} entries
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
            // Resolve dropoff stations based on assignment mode.
            List<DeliveryStation> validStations;
            boolean roundRobin;
            if (!rack.isManualDropoffAssignment()) {
                // Random mode: ignore validDropoffIds, use all stations.
                validStations = new ArrayList<>(allStations);
                roundRobin = false;
            } else {
                // Manual mode: build pool from non-null, resolvable UUIDs only.
                validStations = new ArrayList<>();
                for (UUID uuid : rack.getValidDropoffIds()) {
                    if (uuid == null) continue;
                    DeliveryStation ds = stationById.get(uuid);
                    if (ds != null) validStations.add(ds);
                }
                roundRobin = true;
                if (validStations.isEmpty()) {
                    // Play-time validation (Task 10) prevents this in production.
                    // Defensive skip: don't generate tasks for a broken rack.
                    continue;
                }
            }

            // skip racks with no accessible adjacent tile
            if (!map.hasTraversableAdjacentTile(rack.getPosition())) {
                continue;
            }

            // generate up to boxCount tasks from this rack
            for (int i = 0; i < rack.getBoxCount(); i++) {
                if (tasks.size() >= maxCount) break outer;
                DeliveryStation station = roundRobin
                        ? validStations.get(i % validStations.size())
                        : validStations.get(random.nextInt(validStations.size()));
                int priority = random.nextInt(3) + 1;
                tasks.add(new Task(nextTaskId++,
                    new Vector2D(rack.getPosition().getX(), rack.getPosition().getY()),
                    new Vector2D(station.getPosition().getX(), station.getPosition().getY()),
                    priority));
            }
        }
        return tasks;
    }

    /**
     * Generates exactly {@code count} tasks by randomly sampling rack pickups and delivery station
     * dropoffs, ignoring per-rack box counts and dropoff pools. Used in Automatic task assignment mode.
     *
     * @param map the warehouse map to sample from
     * @param count the exact number of tasks to generate
     * @param seed RNG seed for reproducible output
     * @return list of exactly {@code count} tasks, or an empty list if the map has no racks or stations
     */
    public static List<Task> generateAutomaticTasks(Map map, int count, long seed) {
        List<Vector2D> pickups = new ArrayList<>();
        List<Vector2D> dropoffs = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof Rack r && map.hasTraversableAdjacentTile(r.getPosition()))
                pickups.add(r.getPosition());
            else if (e instanceof DeliveryStation ds)
                dropoffs.add(ds.getPosition());
        }
        if (pickups.isEmpty() || dropoffs.isEmpty()) return new ArrayList<>();

        Random rng = new Random(seed);
        List<Task> tasks = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            Vector2D pickup = pickups.get(rng.nextInt(pickups.size()));
            Vector2D dropoff;
            do { dropoff = dropoffs.get(rng.nextInt(dropoffs.size())); }
            while (dropoff.equals(pickup) && dropoffs.size() > 1);
            tasks.add(new Task(i, new Vector2D(pickup.getX(), pickup.getY()),
                    new Vector2D(dropoff.getX(), dropoff.getY()), rng.nextInt(3) + 1));
        }
        return tasks;
    }

    /**
     * Convenience factory; constructs a {@link TaskGenerator} with the given seed and generates up to {@code count} tasks.
     *
     * @param map the warehouse map to generate tasks for
     * @param count the maximum number of tasks to generate
     * @param seed RNG seed for reproducible output
     * @return list of generated tasks, at most {@code count} entries
     */
    public static List<Task> generateRandomTasks(Map map, int count, long seed) {
        return new TaskGenerator(map, seed).generateTasks(count);
    }
}
