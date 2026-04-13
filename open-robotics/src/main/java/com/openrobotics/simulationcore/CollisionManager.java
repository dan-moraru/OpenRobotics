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

public class CollisionManager {

    // legal checks:
    // - no null intention/from/to
    // - robot id must be present
    // - one tile per tick max by Manhattan distance
    //   and distance 0 means wait/non move, which is fine
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

    /**
     * Helper to determine if a tile allows multiple robots (e.g., a Drop-off point).
     */
    private boolean allowsOverlap(Tile tile) {
        return tile.isDeliveryStation();
    }

    public MoveIntention[] resolveConflicts(MoveIntention[] intentions) {
        return resolveConflicts(null, intentions);
    }

    public MoveIntention[] resolveConflicts(com.openrobotics.map.Map map, MoveIntention[] intentions) {
        if (intentions == null || intentions.length == 0) {
            return new MoveIntention[0];
        }

        // ===== Step 1: keep one legal intention per robot =====
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
                if (entity instanceof Robot robot && robot.getState() == RobotState.BATTER_DEAD) {
                    deadRobotTiles.add(entity.getPosition().getX() + "," + entity.getPosition().getY());
                }
            }
        }
        for (MoveIntention intention : candidates) {
            if (intention.getRobot().getState() == RobotState.BATTER_DEAD) {
                deadRobotTiles.add(tileKey(intention.getFromTile()));
            }
        }

        // ===== Step 2: same-target conflicts =====
        Set<UUID> blockedRobots = new HashSet<>();
        Map<String, List<MoveIntention>> byDestination = new HashMap<>();

        for (MoveIntention intention : candidates) {
            byDestination
                    .computeIfAbsent(tileKey(intention.getToTile()), k -> new ArrayList<>())
                    .add(intention);
        }

        for (Map.Entry<String, List<MoveIntention>> entry : byDestination.entrySet()) {
            List<MoveIntention> group = entry.getValue();
            String destinationKey = entry.getKey();

            if (deadRobotTiles.contains(destinationKey)) {
                for (MoveIntention intention : group) {
                    if (intention.getRobot().getState() != RobotState.BATTER_DEAD) {
                        blockedRobots.add(intention.getRobot().getId());
                    }
                }
                continue;
            }

            if (group.size() <= 1) continue;

            // NEW LOGIC: Check if the destination tile is a delivery point
            Tile targetTile = group.get(0).getToTile();
            if (allowsOverlap(targetTile)) {
                // If it's a delivery point, everyone in this group is allowed to stay/enter.
                // We do NOT add anyone to blockedRobots.
                continue;
            }

            // Standard conflict logic for normal tiles: pick one winner
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

            // Logging robot collision events
            try {
                // Serialize the list of all colliding robot IDs to JSON for logging
                ObjectMapper mapper = new ObjectMapper();
                Map<String, UUID[]> data = new HashMap<>();
                data.put("allCollidingRobots", group.stream().map(i -> i.getRobot().getId()).toArray(UUID[]::new));
                String json = mapper.writeValueAsString(data);

                // Build and log the collision record with the JSON data
                SimulationEngine engine = AppState.getEngine(); // getting the simulation engine from global state
                SimLogRecordBuilder recordBuilder = new SimLogRecordBuilder(engine.getRunId(), engine.getTickCounter(), winnerId, targetTile.getX(), targetTile.getY());
                SimLogRecord record = recordBuilder.buildCollisionRecord(json);
                Logger.logRobotEvent(RobotEvent.COLLISION, record);
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing collision data while logging collision event: " + e.getMessage());
            }
        }

        // ===== Step 3: swap conflicts =====
        // We keep this mostly the same, but we could also allow swaps
        // if the tiles involved allow overlap.
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

                    // Logging robot near miss events for swaps
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

        // ===== Step 4: return approved intentions =====
        List<MoveIntention> approved = new ArrayList<>();
        for (MoveIntention intention : candidates) {
            if (!blockedRobots.contains(intention.getRobot().getId())) {
                approved.add(intention);
            }
        }

        return approved.toArray(new MoveIntention[0]);
    }
}
