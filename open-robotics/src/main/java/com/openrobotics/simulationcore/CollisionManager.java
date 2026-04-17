package com.openrobotics.simulationcore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrobotics.AppState;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.recordbuilders.SimLogRecordBuilder;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.eventtypes.RobotEvent;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;

import java.util.*;

/** Detects and resolves same-target and swap conflicts among move intentions each tick. */
public class CollisionManager {
    // legal checks:
    // - no null intention/from/to
    // - robot id must be present
    // - one tile per tick max by Manhattan distance
    //   and distance 0 means wait/non move, which is fine
    /**
     * Returns {@code true} if the intention is structurally valid for this tick.
     * A legal intention has non-null robot, from-tile, and to-tile, and moves at most one Manhattan step.
     *
     * @param intention the move intention to validate
     * @return {@code true} if the intention is legal; {@code false} otherwise
     */
    public boolean isLegalIntention(MoveIntention intention) {
        if (intention == null ||
                intention.getRobot() == null ||
                intention.getRobot().getId() == null ||
                intention.getFromTile() == null ||
                intention.getToTile() == null) {
            return false;
        }

        int distance = manhattanDistance(intention.getFromTile(), intention.getToTile());
        return distance <= 1;
    }

    private boolean isActualMove(MoveIntention intention) {
        return !sameTile(intention.getFromTile(), intention.getToTile());
    }

    private int manhattanDistance(Tile a, Tile b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    private boolean sameTile(Tile a, Tile b) {
        return a.getX() == b.getX() && a.getY() == b.getY();
    }

    private String tileKey(Tile tile) {
        return tile.getX() + "," + tile.getY();
    }

    // returns true for tiles where multiple robots may occupy simultaneously
    private boolean allowsOverlap(Tile tile) {
        return tile.allowsRobotOverlap();
    }

    /**
     * Resolves conflicts without map context; same-target and swap conflicts are still detected.
     *
     * @param intentions the raw move intentions for this tick
     * @return the approved subset of intentions with conflicts removed
     */
    public MoveIntention[] resolveConflicts(MoveIntention[] intentions) {
        return resolveConflicts(null, intentions);
    }

    /**
     * Resolves same-target, starting-tile occupancy, and swap conflicts among all submitted intentions.
     * Robots blocked by a dead robot's tile are also held. Returns only the approved intentions.
     *
     * @param map the current warehouse map, used to detect dead-robot tile occupancy; may be {@code null}
     * @param intentions the raw move intentions for this tick
     * @return the approved subset of intentions with all detected conflicts removed
     */
    public MoveIntention[] resolveConflicts(com.openrobotics.map.Map map, MoveIntention[] intentions) {
        if (intentions == null || intentions.length == 0) {
            return new MoveIntention[0];
        }

        // step 1: keep one legal intention per robot
        Map<UUID, MoveIntention> uniqueByRobot = new HashMap<>();
        for (MoveIntention intention : intentions) {
            if (!isLegalIntention(intention)) continue;
            uniqueByRobot.putIfAbsent(intention.getRobot().getId(), intention);
        }

        MoveIntention[] candidates = uniqueByRobot.values().toArray(new MoveIntention[0]);
        Arrays.sort(candidates, Comparator.comparing(i -> i.getRobot().getId().toString()));

        Set<String> deadRobotTiles = new HashSet<>();
        if (map != null) {
            for (MapEntity entity : map.getEntities()) {
                if (entity instanceof Robot robot && robot.getState() == RobotState.BATTERY_DEAD) {
                    deadRobotTiles.add(entity.getPosition().getX() + "," + entity.getPosition().getY());
                }
            }
        }
        for (MoveIntention intention : candidates) {
            if (intention.getRobot().getState() == RobotState.BATTERY_DEAD) {
                deadRobotTiles.add(tileKey(intention.getFromTile()));
            }
        }

        // step 2: same-target conflicts
        Set<UUID> blockedRobots = new HashSet<>();
        Map<String, List<MoveIntention>> byDestination = new HashMap<>();
        Map<String, List<MoveIntention>> occupiedAtStart = new HashMap<>();

        for (MoveIntention intention : candidates) {
            byDestination
                    .computeIfAbsent(tileKey(intention.getToTile()), k -> new ArrayList<>())
                    .add(intention);
            occupiedAtStart
                    .computeIfAbsent(tileKey(intention.getFromTile()), k -> new ArrayList<>())
                    .add(intention);
        }

        for (Map.Entry<String, List<MoveIntention>> entry : byDestination.entrySet()) {
            List<MoveIntention> group = entry.getValue();
            String destinationKey = entry.getKey();

            if (deadRobotTiles.contains(destinationKey)) {
                for (MoveIntention intention : group) {
                    if (intention.getRobot().getState() != RobotState.BATTERY_DEAD) {
                        blockedRobots.add(intention.getRobot().getId());
                    }
                }
                continue;
            }

            if (group.size() <= 1) continue;

            // delivery point: all robots in this group may enter simultaneously
            Tile targetTile = group.get(0).getToTile();
            if (allowsOverlap(targetTile)) {
                continue;
            }

            MoveIntention winner = group.stream()
                    .min(Comparator.comparing(i -> i.getRobot().getId().toString()))
                    .get();

            UUID winnerId = winner.getRobot().getId();
            for (MoveIntention intention : group) {
                UUID id = intention.getRobot().getId();
                if (!id.equals(winnerId)) {
                    blockedRobots.add(id);
                }
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, UUID[]> data = new HashMap<>();
                data.put("allCollidingRobots", group.stream().map(i -> i.getRobot().getId()).toArray(UUID[]::new));
                String json = mapper.writeValueAsString(data);

                SimulationEngine engine = AppState.getEngine(); // getting the simulation engine from global state
                SimLogRecordBuilder recordBuilder = new SimLogRecordBuilder(engine.getRunId(), engine.getTickCounter(), winnerId, targetTile.getX(), targetTile.getY());
                SimLogRecord record = recordBuilder.buildCollisionRecord(json);
                Logger.logRobotEvent(RobotEvent.COLLISION, record);
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing collision data while logging collision event: " + e.getMessage());
            }
        }

        // step 3: starting-tile occupancy; a robot's origin tile stays reserved for the whole tick
        // to prevent follow-through where robots appear to pass through each other
        for (MoveIntention intention : candidates) {
            UUID robotId = intention.getRobot().getId();
            if (blockedRobots.contains(robotId) || !isActualMove(intention) || allowsOverlap(intention.getToTile())) {
                continue;
            }

            List<MoveIntention> occupants = occupiedAtStart.get(tileKey(intention.getToTile()));
            if (occupants == null) {
                continue;
            }

            boolean occupiedByOtherRobot = occupants.stream()
                    .anyMatch(occupant -> !occupant.getRobot().getId().equals(robotId));
            if (occupiedByOtherRobot) {
                blockedRobots.add(robotId);
            }
        }

        // step 4: swap conflicts
        for (int i = 0; i < candidates.length; i++) {
            MoveIntention a = candidates[i];
            UUID aId = a.getRobot().getId();

            // If 'a' is already blocked by a target conflict in Step 2, skip it
            if (blockedRobots.contains(aId) || !isActualMove(a)) continue;

            for (int j = i + 1; j < candidates.length; j++) {
                MoveIntention b = candidates[j];
                UUID bId = b.getRobot().getId();

                // If 'b' is already blocked, skip it
                if (blockedRobots.contains(bId) || !isActualMove(b)) continue;

                boolean isSwap = sameTile(a.getToTile(), b.getFromTile()) &&
                        sameTile(b.getToTile(), a.getFromTile());

                if (isSwap) {
                    // Only resolve if NEITHER tile involved allows overlap
                    if (!allowsOverlap(a.getToTile()) && !allowsOverlap(b.getToTile())) {
                        blockedRobots.add(aId);
                        blockedRobots.add(bId);
                    }

                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, UUID> data = new HashMap<>();
                        data.put("otherRobotId", bId);
                        String json = mapper.writeValueAsString(data);

                        SimLogRecordBuilder recordBuilder = new SimLogRecordBuilder(AppState.getEngine().getRunId(), AppState.getEngine().getTickCounter(), aId, a.getToTile().getX(), a.getToTile().getY());
                        SimLogRecord record = recordBuilder.buildNearMissRecord(json);
                        Logger.logRobotEvent(RobotEvent.NEAR_MISS, record);
                    } catch (JsonProcessingException e) {
                        System.err.println("Error serializing near miss data while logging near miss event: " + e.getMessage());
                    }
                }
            }
        }

        // step 5: return approved intentions
        List<MoveIntention> approved = new ArrayList<>();
        for (MoveIntention intention : candidates) {
            if (!blockedRobots.contains(intention.getRobot().getId())) {
                approved.add(intention);
            }
        }

        return approved.toArray(new MoveIntention[0]);
    }
}
