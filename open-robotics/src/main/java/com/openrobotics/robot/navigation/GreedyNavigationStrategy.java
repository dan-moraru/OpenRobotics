package com.openrobotics.robot.navigation;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.sensors.Sensor;
import com.openrobotics.simulationcore.MoveIntention;

import java.util.*;

// greedy navigation: always move toward target by manhattan distance,
// with seeded-random tie breaking and backtracking for dead ends
public class GreedyNavigationStrategy implements NavigationStrategy {
    private final long baseSeed;
    // per-robot navigation state keyed by robot id
    private final java.util.Map<UUID, RobotNavState> navStates = new HashMap<>();

    public GreedyNavigationStrategy(long baseSeed) {
        this.baseSeed = baseSeed;
    }

    // per-robot state: path stack for backtracking, visited set, last known target
    private static class RobotNavState {
        final Deque<Vector2D> pathStack = new ArrayDeque<>();
        final Set<Vector2D> visited = new HashSet<>();
        boolean backtracking = false;
        Vector2D lastTarget = null;
        final Random rng;

        RobotNavState(long seed) {
            this.rng = new Random(seed);
        }
    }

    @Override
    public MoveIntention getNextMove(Robot robot, Map map) {
        Vector2D current = robot.getPosition();
        Tile fromTile = map.getTile(current.getX(), current.getY());
        Vector2D target = robot.getTarget();

        // no target or already at target -> stay in place
        if (target == null || current.equals(target)) {
            return stayIntention(fromTile, robot);
        }

        // Get last scan from robot and see whats blocking
        Set<Vector2D> blockedBySensors = new HashSet<>();
        Sensor sensorScan = robot.getLastScan();
        for (MapEntity entity : sensorScan.getDetectedEntities()) {
            // If robot sees anything, its considered blocked
            blockedBySensors.add(entity.getPosition());
        }

        RobotNavState state = getOrCreateState(robot);

        // reset state if target changed (e.g. pickup -> dropoff)
        if (!target.equals(state.lastTarget)) {
            state.lastTarget = target;
            state.pathStack.clear();
            state.visited.clear();
            state.backtracking = false;
        }

        // mark current as visited and push onto path stack
        state.visited.add(current);
        if (state.pathStack.isEmpty() || !state.pathStack.peek().equals(current)) {
            state.pathStack.push(current);
        }

        // get traversable neighbors excluding visited positions and blocked entities
        List<Vector2D> neighbors = map.getNeighbors(current);
        List<Vector2D> unvisited = new ArrayList<>();
        for (Vector2D n : neighbors) {
            if (!state.visited.contains(n) && !blockedBySensors.contains(n)) {
                unvisited.add(n);
            }
        }

        int currentDist = current.manhattanDistance(target);

        // partition unvisited neighbors into closer, sideways, farther
        List<Vector2D> closer = new ArrayList<>();
        List<Vector2D> sideways = new ArrayList<>();
        List<Vector2D> farther = new ArrayList<>();
        for (Vector2D n : unvisited) {
            int d = n.manhattanDistance(target);
            if (d < currentDist) closer.add(n);
            else if (d == currentDist) sideways.add(n);
            else farther.add(n);
        }

        Vector2D next = null;

        // priority: closer > sideways > farther > backtrack
        if (!closer.isEmpty()) {
            next = pickBest(closer, target, state.rng);
            state.backtracking = false;
        } else if (!sideways.isEmpty() && !state.backtracking) {
            next = pickBest(sideways, target, state.rng);
        } else if (!farther.isEmpty() && !state.backtracking) {
            // farther lets robot detour around obstacles instead of backtracking early
            next = pickBest(farther, target, state.rng);
        } else {
            // no unvisited neighbors — retrace via path stack
            state.backtracking = true;
            if (!state.pathStack.isEmpty() && state.pathStack.peek().equals(current)) {
                state.pathStack.pop(); // pop current so we step back
            }
            if (!state.pathStack.isEmpty()) {
                next = state.pathStack.peek();
            }
        }

        if (next == null) {
            return stayIntention(fromTile, robot);
        }

        Tile toTile = map.getTile(next.getX(), next.getY());
        return new MoveIntention(fromTile, toTile, robot);
    }

    // seeded random tie-break among candidates (spec cs7: deterministic)
    private Vector2D pickBest(List<Vector2D> candidates, Vector2D target, Random rng) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        // find minimum manhattan distance among candidates
        int minDist = Integer.MAX_VALUE;
        for (Vector2D c : candidates) {
            int d = c.manhattanDistance(target);
            if (d < minDist) minDist = d;
        }

        // collect all candidates at minimum distance
        List<Vector2D> best = new ArrayList<>();
        for (Vector2D c : candidates) {
            if (c.manhattanDistance(target) == minDist) {
                best.add(c);
            }
        }
        return best.get(rng.nextInt(best.size()));
    }

    // per-robot state with seed = baseseed xor uuid hash (spec cs7 determinism)
    private RobotNavState getOrCreateState(Robot robot) {
        return navStates.computeIfAbsent(robot.getId(), id -> new RobotNavState(baseSeed ^ id.hashCode()));
    }

    private MoveIntention stayIntention(Tile tile, Robot robot) {
        return new MoveIntention(tile, tile, robot);
    }

    @Override
    public String toString() {
        return "GREEDY";
    }
}
