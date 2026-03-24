package com.openrobotics.task;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TaskGenerator {
    private final Map map;
    private final Random random;
    private final List<Vector2D> validLocations;

    public TaskGenerator(Map map, long seed) {
        this.map = map;
        this.random = new Random(seed);
        this.validLocations = computeValidLocations();
    }

    private List<Vector2D> computeValidLocations() {
        List<Vector2D> locations = new ArrayList<>();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Vector2D pos = new Vector2D(x, y);
                if (isValidTaskLocation(pos)) {
                    locations.add(pos);
                }
            }
        }
        return locations;
    }

    private boolean isValidTaskLocation(Vector2D pos) {
        if (!map.isTraversable(pos)) {
            return false;
        }
        for (MapEntity entity : map.getEntities()) {
            if (entity.getPosition().equals(pos)) {
                if (entity instanceof com.openrobotics.map.entities.environment.Obstacle) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<Task> generateTasks(int count) {
        List<Task> tasks = new ArrayList<>();
        if (validLocations.size() < 2) {
            return tasks;
        }

        List<Vector2D> shuffled = new ArrayList<>(validLocations);
        Collections.shuffle(shuffled, random);

        for (int i = 0; i < count && i * 2 + 1 < shuffled.size(); i++) {
            Vector2D pickup = shuffled.get(i * 2);
            Vector2D dropoff = shuffled.get(i * 2 + 1);
            int priority = random.nextInt(3) + 1;
            tasks.add(new Task(i + 1, pickup, dropoff, priority));
        }
        return tasks;
    }

    public static List<Task> generateRandomTasks(Map map, int count, long seed) {
        TaskGenerator generator = new TaskGenerator(map, seed);
        return generator.generateTasks(count);
    }
}
