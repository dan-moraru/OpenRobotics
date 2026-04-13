package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;

import java.util.*;

/**
 * reservation-k coordination policy; a robot must hold locks for the next k tiles on its path before it can move.
 * as the robot progresses, tiles it already reached are released.
 */
public class ReservationKPolicy implements CoordinationPolicy {
    private final int k;

    // Tracks which robot currently owns each reserved tile.
    private final java.util.Map<String, UUID> tileOwners = new HashMap<>();

    // Tracks the ordered reservation window for each robot.
    private final java.util.Map<UUID, Deque<String>> robotReservations = new HashMap<>();

    public ReservationKPolicy(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        this.k = k;
    }

    @Override
    public MoveIntention[] apply(Map map, MoveIntention[] intentions) {
        MoveIntention[] ordered = sortByRobotId(copyNonNull(intentions));
        List<MoveIntention> result = new ArrayList<>(ordered.length);
        Set<String> approvedEdges = new HashSet<>();

        for (MoveIntention intention : ordered) {
            if (intention == null || intention.getRobot() == null) {
                continue;
            }

            UUID robotId = intention.getRobot().getId();

            if (!hasTiles(intention) || !isActualMove(intention)) {
                // If the robot is not moving this tick, it should not keep path locks.
                releaseAllReservations(robotId);
                result.add(intention);
                continue;
            }

            Tile from = intention.getFromTile();
            Tile to = intention.getToTile();

            // Once the robot has physically reached a reserved tile, release it.
            syncReservationsWithCurrentPosition(robotId, from);

            String edge = edgeKey(from, to);
            String reverseEdge = edgeKey(to, from);
            if (approvedEdges.contains(reverseEdge)) {
                result.add(forceWait(intention));
                continue;
            }

            List<String> reservationWindow = buildReservationWindow(map, intention);
            if (!canAcquireWindow(robotId, reservationWindow)) {
                result.add(forceWait(intention));
                continue;
            }

            applyReservationWindow(robotId, reservationWindow);
            approvedEdges.add(edge);
            result.add(intention);
        }

        return result.toArray(new MoveIntention[0]);
    }

    @Override
    public void clearRobotCoordinationState(Robot robot) {
        // Deadlock handling should immediately drop any path locks owned by this robot.
        if (robot != null) {
            releaseAllReservations(robot.getId());
        }
    }

    // Builds the next k tiles the robot wants to occupy.
    // The current move is always the first tile in the window.
    private List<String> buildReservationWindow(Map map, MoveIntention intention) {
        List<String> window = new ArrayList<>();
        Tile nextTile = intention.getToTile();
        window.add(tileKey(nextTile));

        Vector2D target = intention.getRobot().getTarget();
        if (map == null || target == null) {
            return window;
        }

        Vector2D start = nextTile.getPosition();
        if (start.equals(target)) {
            return window;
        }

        List<Vector2D> remainderPath = findShortestPath(map, start, target);
        for (Vector2D step : remainderPath) {
            if (window.size() >= k) {
                break;
            }
            String key = tileKey(step.getX(), step.getY());
            if (!window.contains(key)) {
                window.add(key);
            }
        }

        return window;
    }

    // Simple BFS path preview used only for reservation lookahead.
    // It keeps the implementation predictable and independent from collision state.
    private List<Vector2D> findShortestPath(Map map, Vector2D start, Vector2D target) {
        Deque<Vector2D> queue = new ArrayDeque<>();
        java.util.Map<Vector2D, Vector2D> previous = new HashMap<>();
        Set<Vector2D> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Vector2D current = queue.removeFirst();
            if (current.equals(target)) {
                break;
            }

            for (Vector2D neighbor : map.getNeighbors(current)) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                visited.add(neighbor);
                previous.put(neighbor, current);
                queue.addLast(neighbor);
            }
        }

        if (!visited.contains(target)) {
            return List.of();
        }

        List<Vector2D> path = new ArrayList<>();
        Vector2D current = target;
        while (!current.equals(start)) {
            path.add(0, current);
            current = previous.get(current);
            if (current == null) {
                return List.of();
            }
        }
        return path;
    }

    // A window is acquirable if every tile is either free or already owned by this robot.
    private boolean canAcquireWindow(UUID robotId, List<String> reservationWindow) {
        for (String tileKey : reservationWindow) {
            UUID owner = tileOwners.get(tileKey);
            if (owner != null && !owner.equals(robotId)) {
                return false;
            }
        }
        return true;
    }

    // Replace the robot's old window with the new one.
    // This keeps reservations aligned with the robot's latest planned path.
    private void applyReservationWindow(UUID robotId, List<String> reservationWindow) {
        releaseAllReservations(robotId);

        Deque<String> newWindow = new ArrayDeque<>();
        for (String tileKey : reservationWindow) {
            tileOwners.put(tileKey, robotId);
            newWindow.addLast(tileKey);
        }
        robotReservations.put(robotId, newWindow);
    }

    // Releases any reserved tiles the robot has already reached.
    private void syncReservationsWithCurrentPosition(UUID robotId, Tile currentTile) {
        Deque<String> reservations = robotReservations.get(robotId);
        if (reservations == null || currentTile == null) {
            return;
        }

        String currentKey = tileKey(currentTile);
        while (!reservations.isEmpty() && reservations.peekFirst().equals(currentKey)) {
            String released = reservations.removeFirst();
            if (robotId.equals(tileOwners.get(released))) {
                tileOwners.remove(released);
            }
        }

        if (reservations.isEmpty()) {
            robotReservations.remove(robotId);
        }
    }

    private void releaseAllReservations(UUID robotId) {
        Deque<String> reservations = robotReservations.remove(robotId);
        if (reservations == null) {
            return;
        }

        while (!reservations.isEmpty()) {
            String reservedTile = reservations.removeFirst();
            if (robotId.equals(tileOwners.get(reservedTile))) {
                tileOwners.remove(reservedTile);
            }
        }
    }

    private MoveIntention[] copyNonNull(MoveIntention[] intentions) {
        if (intentions == null || intentions.length == 0) {
            return new MoveIntention[0];
        }

        List<MoveIntention> list = new ArrayList<>();
        for (MoveIntention intention : intentions) {
            if (intention != null) {
                list.add(intention);
            }
        }
        return list.toArray(new MoveIntention[0]);
    }

    private MoveIntention[] sortByRobotId(MoveIntention[] intentions) {
        if (intentions == null || intentions.length == 0) {
            return new MoveIntention[0];
        }

        Arrays.sort(intentions, java.util.Comparator.comparing(
                (MoveIntention i) -> i == null || i.getRobot() == null ? null : i.getRobot().getId(),
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())
        ));
        return intentions;
    }

    private boolean hasTiles(MoveIntention intention) {
        return intention.getFromTile() != null && intention.getToTile() != null;
    }

    private boolean isActualMove(MoveIntention intention) {
        Tile from = intention.getFromTile();
        Tile to = intention.getToTile();
        return from.getX() != to.getX() || from.getY() != to.getY();
    }

    private MoveIntention forceWait(MoveIntention intention) {
        return new MoveIntention(
                intention.getFromTile(),
                intention.getFromTile(),
                intention.getRobot()
        );
    }

    private String tileKey(Tile tile) {
        return tileKey(tile.getX(), tile.getY());
    }

    private String tileKey(int x, int y) {
        return x + "," + y;
    }

    private String edgeKey(Tile from, Tile to) {
        return tileKey(from) + "->" + tileKey(to);
    }

    public int getK() {
        return this.k;
    }
}
