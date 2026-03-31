package com.openrobotics.robot.navigation;

import com.openrobotics.common.Direction;
import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.sensors.Sensor;
import com.openrobotics.simulationcore.MoveIntention;

import java.util.*;

// bug2 navigation: greedy goal-seeking with left-hand boundary following on obstacles
public class BugNavigationStrategy implements NavigationStrategy {
    private final long baseSeed;
    // per-robot navigation state keyed by robot id (same pattern as greedy)
    private final java.util.Map<UUID, BugNavState> navStates = new HashMap<>();

    public BugNavigationStrategy(long baseSeed) {
        this.baseSeed = baseSeed;
    }

    // per-robot state for bug2 algorithm
    private static class BugNavState {
        Vector2D lastTarget = null;          // detect target changes (pickup -> dropoff)
        boolean followingBoundary = false;   // true = wall-following mode, false = greedy mode
        Vector2D hitPoint = null;            // where we entered boundary-following
        int hitPointDistance = Integer.MAX_VALUE; // manhattan dist at hit point (for exit condition)
        Direction heading = Direction.RIGHT; // current facing direction during wall-following
        Set<Vector2D> mLine = new HashSet<>(); // straight line from start to target (bresenham)
        int boundarySteps = 0;              // steps taken in boundary mode (for loop detection)

        // collision rejection recovery: save state before mutating, revert if move was rejected
        Vector2D lastIntendedPos = null;
        Direction prevHeading = Direction.RIGHT;
        boolean prevFollowingBoundary = false;

        final Random rng;

        BugNavState(long seed) {
            this.rng = new Random(seed);
        }

        void reset() {
            followingBoundary = false;
            hitPoint = null;
            hitPointDistance = Integer.MAX_VALUE;
            heading = Direction.RIGHT;
            mLine.clear();
            boundarySteps = 0;
            lastIntendedPos = null;
            prevHeading = Direction.RIGHT;
            prevFollowingBoundary = false;
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

        Set<Vector2D> blocked = getSensorBlockedPositions(robot, target);
        BugNavState state = getOrCreateState(robot);

        // if collisionmanager rejected our last move, revert heading/mode changes
        if (state.lastIntendedPos != null && !current.equals(state.lastIntendedPos)) {
            state.heading = state.prevHeading;
            state.followingBoundary = state.prevFollowingBoundary;
            if (state.boundarySteps > 0) {
                state.boundarySteps--;
            }
        }
        state.lastIntendedPos = null;

        // reset state on target change, recompute m-line (bresenham line to new target)
        if (!target.equals(state.lastTarget)) {
            state.lastTarget = target;
            state.reset();
            state.mLine = computeMLine(current, target);
        }

        // bug2 termination: looped back to hit point means target is unreachable
        if (state.followingBoundary && state.boundarySteps > 0
                && current.equals(state.hitPoint)) {
            return stayIntention(fromTile, robot);
        }

        // bug2 exit: on m-line and closer than hit point -> switch back to greedy
        if (state.followingBoundary && state.mLine.contains(current)
                && current.manhattanDistance(target) < state.hitPointDistance) {
            state.followingBoundary = false;
        }

        // dispatch to greedy (reduce distance) or boundary (follow wall)
        if (!state.followingBoundary) {
            return greedyStep(robot, map, current, target, blocked, state, fromTile);
        } else {
            return boundaryStep(robot, map, current, target, blocked, state, fromTile);
        }
    }

    // greedy mode: move strictly closer by manhattan distance, else enter boundary-following
    private MoveIntention greedyStep(Robot robot, Map map, Vector2D current,
            Vector2D target, Set<Vector2D> blocked, BugNavState state, Tile fromTile) {
        List<Vector2D> neighbors = map.getNeighbors(current);
        int currentDist = current.manhattanDistance(target);

        // only consider neighbors that strictly reduce manhattan distance
        List<Vector2D> closer = new ArrayList<>();
        for (Vector2D n : neighbors) {
            if (!blocked.contains(n) && n.manhattanDistance(target) < currentDist) {
                closer.add(n);
            }
        }

        if (!closer.isEmpty()) {
            // prefer candidates that have at least 2 clear steps ahead (range sensor only).
            // if none qualify, use the full closer list; this is a soft preference, not a hard filter.
            // proximity robots always return maxDist from knownClearanceToward, so all candidates qualify and nothing changes.
            Sensor scan = robot.getLastScan();
            List<Vector2D> preferred = new ArrayList<>();
            if (scan != null) {
                for (Vector2D c : closer) {
                    if (scan.knownClearanceToward(current, c, 5) >= 2) preferred.add(c);
                }
            }
            List<Vector2D> pool = preferred.isEmpty() ? closer : preferred;
            Vector2D next = pickBest(pool, current, target, state.rng, scan);
            // save snapshot so rollback restores greedy-time state, not stale boundary state
            state.prevHeading = state.heading;
            state.prevFollowingBoundary = state.followingBoundary;
            state.lastIntendedPos = next;
            Tile toTile = map.getTile(next.getX(), next.getY());
            return new MoveIntention(fromTile, toTile, robot);
        }

        // no closer neighbor; enter boundary-following mode
        // turn right from approach direction so the wall is on the left (left-hand rule)
        state.followingBoundary = true;
        state.hitPoint = current;
        state.hitPointDistance = currentDist;
        state.heading = turnRight(bestDirectionToward(current, target));
        state.boundarySteps = 0;

        return boundaryStep(robot, map, current, target, blocked, state, fromTile);
    }

    // left-hand wall following: try turn-left, straight, turn-right, reverse
    // traces obstacle boundary until m-line exit condition is met
    private MoveIntention boundaryStep(Robot robot, Map map, Vector2D current,
            Vector2D target, Set<Vector2D> blocked, BugNavState state, Tile fromTile) {
        // left-hand rule priority: turn toward wall, go straight, turn away, reverse
        Direction[] tryOrder = {
            turnLeft(state.heading), state.heading,
            turnRight(state.heading), opposite(state.heading)
        };

        for (Direction dir : tryOrder) {
            Vector2D neighbor = current.add(dir.dx, dir.dy);
            Tile tile = map.getTile(neighbor.getX(), neighbor.getY());
            if (tile == null) continue;
            if (!map.isTraversable(neighbor)) continue;
            if (blocked.contains(neighbor)) continue;

            // save snapshot before mutating state (for collision rejection recovery)
            state.prevHeading = state.heading;
            state.prevFollowingBoundary = state.followingBoundary;

            // update heading to the direction we actually move
            state.heading = dir;
            state.boundarySteps++;

            // m-line exit: destination on m-line and closer than hit point -> leave boundary
            if (state.mLine.contains(neighbor)
                    && neighbor.manhattanDistance(target) < state.hitPointDistance) {
                state.followingBoundary = false;
            }

            // record intended position so we can detect collision rejection next tick
            state.lastIntendedPos = neighbor;
            return new MoveIntention(fromTile, tile, robot);
        }

        // completely stuck
        return stayIntention(fromTile, robot);
    }

    // collect sensor-blocked positions (obstacles only, null-safe, target-exempt)
    private Set<Vector2D> getSensorBlockedPositions(Robot robot, Vector2D target) {
        Set<Vector2D> blocked = new HashSet<>();
        Sensor scan = robot.getLastScan();
        if (scan != null) {
            for (MapEntity entity : scan.getDetectedEntities()) {
                if (entity instanceof Obstacle) {
                    blocked.add(entity.getPosition());
                }
            }
        }
        blocked.remove(target);
        return blocked;
    }

    // bresenham line rasterization from start to target, used as bug2 m-line
    // returns every grid cell on the straight line between two points
    private Set<Vector2D> computeMLine(Vector2D from, Vector2D to) {
        Set<Vector2D> line = new HashSet<>();
        int x0 = from.getX(), y0 = from.getY();
        int x1 = to.getX(), y1 = to.getY();
        // absolute distance in each axis
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        // step direction: +1 or -1 depending on which way we're going
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        // error term tracks when to step in x vs y
        int err = dx - dy;

        while (true) {
            line.add(new Vector2D(x0, y0));
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            // step horizontally if error favors x
            if (e2 > -dy) { err -= dy; x0 += sx; }
            // step vertically if error favors y
            if (e2 < dx) { err += dx; y0 += sy; }
        }
        return line;
    }

    // cardinal direction closest to the vector from -> to
    private Direction bestDirectionToward(Vector2D from, Vector2D to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        }
    }

    // 90-degree counterclockwise rotation
    private Direction turnLeft(Direction d) {
        return switch (d) {
            case UP -> Direction.LEFT;
            case LEFT -> Direction.DOWN;
            case DOWN -> Direction.RIGHT;
            case RIGHT -> Direction.UP;
        };
    }

    // 90-degree clockwise rotation
    private Direction turnRight(Direction d) {
        return switch (d) {
            case UP -> Direction.RIGHT;
            case RIGHT -> Direction.DOWN;
            case DOWN -> Direction.LEFT;
            case LEFT -> Direction.UP;
        };
    }

    // 180-degree reversal
    private Direction opposite(Direction d) {
        return switch (d) {
            case UP -> Direction.DOWN;
            case DOWN -> Direction.UP;
            case LEFT -> Direction.RIGHT;
            case RIGHT -> Direction.LEFT;
        };
    }

    // seeded random tie-break among candidates closest to target.
    // tie-breaking order: min manhattan distance -> max sensor clearance -> seeded rng.
    // proximity sensors return maxDist (unknown = neutral), so behavior is unchanged for them
    private Vector2D pickBest(List<Vector2D> candidates, Vector2D current,
            Vector2D target, Random rng, Sensor scan) {
        if (candidates.size() == 1) return candidates.get(0);
        // find the shortest manhattan distance among all candidates
        int minDist = Integer.MAX_VALUE;
        for (Vector2D c : candidates) {
            int d = c.manhattanDistance(target);
            if (d < minDist) minDist = d;
        }
        // collect all candidates tied at that shortest distance
        List<Vector2D> best = new ArrayList<>();
        for (Vector2D c : candidates) {
            if (c.manhattanDistance(target) == minDist) best.add(c);
        }
        // sensor clearance tie-break: prefer directions with more known open space ahead
        if (scan != null && best.size() > 1) {
            int maxClearance = -1;
            for (Vector2D c : best) {
                int cl = scan.knownClearanceToward(current, c, 5);
                if (cl > maxClearance) maxClearance = cl;
            }
            List<Vector2D> clearest = new ArrayList<>();
            for (Vector2D c : best) {
                if (scan.knownClearanceToward(current, c, 5) == maxClearance) clearest.add(c);
            }
            best = clearest;
        }
        // pick randomly among ties (seeded rng for determinism)
        return best.get(rng.nextInt(best.size()));
    }

    // per-robot state with seed = baseseed xor uuid hash (deterministic per robot)
    private BugNavState getOrCreateState(Robot robot) {
        return navStates.computeIfAbsent(robot.getId(), id -> new BugNavState(baseSeed ^ id.hashCode()));
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
        return "BUG";
    }
}
