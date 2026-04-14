package com.openrobotics.robot.navigation;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.sensors.Sensor;
import com.openrobotics.simulationcore.MoveIntention;

import java.util.*;

/** real-time A* navigation; learns heuristic values during exploration and penalizes sensor-detected obstacles */
public class RtaStarNavigationStrategy implements NavigationStrategy {
    private final long baseSeed;
    // per-robot navigation state keyed by robot id (same pattern as greedy/bug)
    private final java.util.Map<UUID, RtaStarNavState> navStates = new HashMap<>();

    public RtaStarNavigationStrategy(long baseSeed) {
        this.baseSeed = baseSeed;
    }

    // per-robot state: learned heuristic table and last target for reset detection
    private static class RtaStarNavState {
        Vector2D lastTarget = null;
        // learned heuristic: tile -> estimated distance, starts as manhattan, grows at dead ends
        final java.util.Map<Vector2D, Integer> hTable = new HashMap<>();
        final Random rng;

        RtaStarNavState(long seed) {
            this.rng = new Random(seed);
        }
    }

    @Override
    public MoveIntention getNextMove(Robot robot, Map map) {
        Vector2D current = robot.getPosition();
        Tile fromTile = map.getTile(current.getX(), current.getY());
        Vector2D target = robot.getTarget();

        // no target -> stay in place
        if (target == null) return stayIntention(fromTile, robot);
        // arrived: adjacent for racks, on-tile for everything else
        boolean arrived = map.isRackAt(target)
            ? current.manhattanDistance(target) == 1
            : current.equals(target);
        if (arrived) return stayIntention(fromTile, robot);

        Set<Vector2D> blocked = getSensorBlockedPositions(robot, target);
        RtaStarNavState state = getOrCreateState(robot);

        // reset heuristic table on target change (old values are meaningless)
        if (!target.equals(state.lastTarget)) {
            state.lastTarget = target;
            state.hTable.clear();
        }

        // get adjacent walkable tiles (map already filters out obstacles and out-of-bounds)
        List<Vector2D> neighbors = map.getNeighbors(current);
        // further filter out tiles that the sensor detected as blocked
        List<Vector2D> candidates = new ArrayList<>();
        for (Vector2D n : neighbors) {
            if (!blocked.contains(n)) {
                candidates.add(n);
            }
        }

        // no moves available; stay in place, skip learning
        if (candidates.isEmpty()) {
            return stayIntention(fromTile, robot);
        }

        // two arrays because the sensor penalty shouldnt be saved into hTable.
        // fRaw is the clean score (1 + h) used only for learning - no sensor data.
        // fValues adds a sensor penalty on top and is used only to pick a move this tick.
        // if we used fValues for learning, a one-time sensor reading would permanently
        // inflate the heuristic for that tile and make the robot avoid it forever.
        Sensor scan = robot.getLastScan();
        int[] fValues = new int[candidates.size()];
        int[] fRaw    = new int[candidates.size()];
        int minF = Integer.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Vector2D n = candidates.get(i);
            // h is the learned cost for this tile if visited before, otherwise plain manhattan distance
            int h = state.hTable.getOrDefault(n, n.manhattanDistance(target));
            // sensor penalty: +1 to any candidate where an obstacle is detected within 2 steps ahead,
            // making it less preferred since RTA* picks the lowest score. proximity always gives 0 penalty
            int clearance = (scan != null) ? scan.knownClearanceToward(current, n, 5) : 5;
            int sensorPenalty = (clearance < 3) ? 1 : 0;
            fRaw[i]    = 1 + h;
            fValues[i] = 1 + h + sensorPenalty;
            if (fValues[i] < minF) minF = fValues[i];
        }

        // collect all candidates tied at the best f-value
        List<Integer> tieIndices = new ArrayList<>();
        for (int i = 0; i < fValues.length; i++) {
            if (fValues[i] == minF) tieIndices.add(i);
        }
        // pick randomly among ties (seeded rng for determinism)
        int chosenIdx = tieIndices.get(state.rng.nextInt(tieIndices.size()));
        Vector2D chosen = candidates.get(chosenIdx);

        // learn: update current cell's h so future visits make better decisions.
        // uses fRaw (not fValues) so sensor penalties do not accumulate in the heuristic table
        int learnedValue;
        if (candidates.size() == 1) {
            // only one option, learn its cost
            learnedValue = fRaw[0];
        } else {
            // find second-smallest fRaw in single pass (no sorting needed)
            // second-best because best is where we just moved to
            int smallest = Integer.MAX_VALUE;
            int secondSmallest = Integer.MAX_VALUE;
            for (int f : fRaw) {
                if (f <= smallest) {
                    secondSmallest = smallest;
                    smallest = f;
                } else if (f < secondSmallest) {
                    secondSmallest = f;
                }
            }
            learnedValue = secondSmallest;
        }
        // h never decreases; only update if learned value is higher than current
        int currentH = state.hTable.getOrDefault(current, current.manhattanDistance(target));
        state.hTable.put(current, Math.max(currentH, learnedValue));

        Tile toTile = map.getTile(chosen.getX(), chosen.getY());
        return new MoveIntention(fromTile, toTile, robot);
    }

    // collect sensor-blocked positions: only obstacles count as blockers,
    // racks/stations are valid destinations, robot conflicts handled by collisionmanager
    private Set<Vector2D> getSensorBlockedPositions(Robot robot, Vector2D target) {
        Set<Vector2D> blocked = new HashSet<>();
        Sensor scan = robot.getLastScan();
        // null-safe: robots without sensors still work
        if (scan != null) {
            for (MapEntity entity : scan.getDetectedEntities()) {
                if (entity instanceof Obstacle) {
                    blocked.add(entity.getPosition());
                }
            }
        }
        // never block the target tile (so robots can reach their destination)
        blocked.remove(target);
        if (robot.getRerouteAvoidTile() != null && !robot.getRerouteAvoidTile().equals(target)) {
            blocked.add(robot.getRerouteAvoidTile());
        }
        return blocked;
    }

    // per-robot state with seed = baseseed xor uuid hash (deterministic per robot)
    private RtaStarNavState getOrCreateState(Robot robot) {
        return navStates.computeIfAbsent(robot.getId(), id -> new RtaStarNavState(baseSeed ^ id.hashCode()));
    }

    // stay-in-place intention (fromTile == toTile)
    private MoveIntention stayIntention(Tile tile, Robot robot) {
        return new MoveIntention(tile, tile, robot);
    }

    @Override
    public void reset(Robot robot) {
        // Deadlock recovery starts a fresh search for this robot's next assignment.
        if (robot != null) {
            navStates.remove(robot.getId());
        }
    }

    @Override
    public String toString() {
        return "RTA_STAR";
    }
}
