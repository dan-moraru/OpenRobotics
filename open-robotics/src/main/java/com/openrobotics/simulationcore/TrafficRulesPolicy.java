package com.openrobotics.simulationcore;
import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.robot.Robot;

import java.util.*;

public class TrafficRulesPolicy implements CoordinationPolicy {
    private final Set<Tile> intersections;
    private final Set<String> intersectionKeys;

    // Robots waiting to enter the intersection this tick.
    private final java.util.Map<UUID, IntersectionRequest> intersectionQueue = new HashMap<>();

    // The robot currently inside the intersection area, if any.
    private UUID activeIntersectionRobotId;

    public TrafficRulesPolicy(Set<Tile> intersectionTiles) {
        this.intersections = (intersectionTiles == null)
                ? new HashSet<>()
                : new HashSet<>(intersectionTiles);
        this.intersectionKeys = new HashSet<>();

        for (Tile tile : this.intersections) {
            if (tile != null) {
                intersectionKeys.add(tileKey(tile));
            }
        }
    }

    @Override
    public MoveIntention[] apply(Map map, MoveIntention[] intentions) {
        MoveIntention[] ordered = sortByRobotId(copyNonNull(intentions));
        List<MoveIntention> result = new ArrayList<>(ordered.length);

        java.util.Map<UUID, Robot> robotsById = collectRobotsById(ordered);

        // Release state that is no longer valid before evaluating this tick.
        releaseIntersectionIfOwnerExited(robotsById);
        refreshIntersectionQueue(ordered);

        UUID queueHead = getIntersectionQueueHead();

        for (MoveIntention intention : ordered) {
            Robot robot = intention.getRobot();
            UUID robotId = robot.getId();

            if (!hasTiles(intention)) {
                result.add(intention);
                continue;
            }

            Tile from = intention.getFromTile();
            Tile to = intention.getToTile();
            boolean actualMove = isActualMove(intention);
            boolean fromIntersection = isIntersectionTile(from);
            boolean toIntersection = isIntersectionTile(to);

            // A robot already inside the intersection keeps exclusive access
            // until it fully leaves the intersection area.
            if (fromIntersection) {
                if (activeIntersectionRobotId == null) {
                    activeIntersectionRobotId = robotId;
                }
                if (!activeIntersectionRobotId.equals(robotId)) {
                    result.add(forceWait(intention));
                    continue;
                }
            }

            // A robot entering the intersection must first reach the head of the queue.
            if (actualMove && !fromIntersection && toIntersection) {
                if (activeIntersectionRobotId != null && !activeIntersectionRobotId.equals(robotId)) {
                    result.add(forceWait(intention));
                    continue;
                }
                if (queueHead == null || !queueHead.equals(robotId)) {
                    result.add(forceWait(intention));
                    continue;
                }

                activeIntersectionRobotId = robotId;
                intersectionQueue.remove(robotId);
            }

            result.add(intention);
        }

        return result.toArray(new MoveIntention[0]);
    }

    // Refreshes the queue from the robots that are actively trying to enter
    // the intersection this tick. Priority is recomputed each tick so stop time
    // and load status can change naturally over time.
    private void refreshIntersectionQueue(MoveIntention[] intentions) {
        Set<UUID> currentEntrants = new HashSet<>();

        for (MoveIntention intention : intentions) {
            if (!hasTiles(intention) || !isActualMove(intention)) {
                continue;
            }
            if (!isEnteringIntersection(intention)) {
                continue;
            }

            Robot robot = intention.getRobot();
            UUID robotId = robot.getId();
            currentEntrants.add(robotId);
            intersectionQueue.put(robotId, new IntersectionRequest(
                    robotId,
                    robot.getStuckTicks(),
                    robot.isHasPickedUp()
            ));
        }

        Iterator<java.util.Map.Entry<UUID, IntersectionRequest>> iterator = intersectionQueue.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<UUID, IntersectionRequest> entry = iterator.next();
            UUID robotId = entry.getKey();
            if (!currentEntrants.contains(robotId) && !robotId.equals(activeIntersectionRobotId)) {
                iterator.remove();
            }
        }
    }

    private UUID getIntersectionQueueHead() {
        return intersectionQueue.values().stream()
                .sorted(intersectionPriorityComparator())
                .map(request -> request.robotId)
                .findFirst()
                .orElse(null);
    }

    // Longer waits win first, loaded robots win next,
    // and robot id breaks any remaining ties deterministically.
    private Comparator<IntersectionRequest> intersectionPriorityComparator() {
        return Comparator
                .comparingInt((IntersectionRequest request) -> request.stopTime).reversed()
                .thenComparing((IntersectionRequest request) -> request.loaded ? 0 : 1)
                .thenComparing(request -> request.robotId.toString());
    }

    private void releaseIntersectionIfOwnerExited(java.util.Map<UUID, Robot> robotsById) {
        if (activeIntersectionRobotId == null) {
            return;
        }

        Robot owner = robotsById.get(activeIntersectionRobotId);
        if (owner == null) {
            activeIntersectionRobotId = null;
            return;
        }

        String currentTileKey = tileKey(owner.getPosition().getX(), owner.getPosition().getY());
        if (!intersectionKeys.contains(currentTileKey)) {
            activeIntersectionRobotId = null;
        }
    }

    private java.util.Map<UUID, Robot> collectRobotsById(MoveIntention[] intentions) {
        java.util.Map<UUID, Robot> robotsById = new HashMap<>();
        for (MoveIntention intention : intentions) {
            if (intention != null && intention.getRobot() != null) {
                robotsById.put(intention.getRobot().getId(), intention.getRobot());
            }
        }
        return robotsById;
    }

    private boolean isEnteringIntersection(MoveIntention intention) {
        return hasTiles(intention)
                && isActualMove(intention)
                && !isIntersectionTile(intention.getFromTile())
                && isIntersectionTile(intention.getToTile());
    }

    private boolean isIntersectionTile(Tile tile) {
        return tile != null && intersectionKeys.contains(tileKey(tile));
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

        Arrays.sort(intentions, Comparator.comparing(
                (MoveIntention i) -> i == null || i.getRobot() == null ? null : i.getRobot().getId(),
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));
        return intentions;
    }

    public Set<Tile> getIntersectionTiles() {
        return new HashSet<>(intersections);
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

    private static class IntersectionRequest {
        private final UUID robotId;
        private final int stopTime;
        private final boolean loaded;

        private IntersectionRequest(UUID robotId, int stopTime, boolean loaded) {
            this.robotId = robotId;
            this.stopTime = stopTime;
            this.loaded = loaded;
        }
    }
}
