package com.openrobotics.simulationcore;

import com.openrobotics.map.Tile;

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
        }

        // ===== Step 3: swap conflicts =====
        // We keep this mostly the same, but we could also allow swaps
        // if the tiles involved allow overlap.
        for (int i = 0; i < candidates.length; i++) {
            MoveIntention a = candidates[i];
            UUID aId = a.getRobot().getId();
            if (blockedRobots.contains(aId) || !isActualMove(a)) continue;

            for (int j = i + 1; j < candidates.length; j++) {
                MoveIntention b = candidates[j];
                UUID bId = b.getRobot().getId();
                if (blockedRobots.contains(bId) || !isActualMove(b)) continue;

                boolean isSwap = sameTile(a.getToTile(), b.getFromTile()) &&
                        sameTile(b.getToTile(), a.getFromTile());

                if (isSwap) {
                    // Only resolve if NEITHER tile involved allows overlap
                    if (!allowsOverlap(a.getToTile()) && !allowsOverlap(b.getToTile())) {
                        // Same tie-break as same-destination: lexicographically smallest UUID wins
                        MoveIntention winner = Comparator
                                .comparing((MoveIntention mi) -> mi.getRobot().getId().toString())
                                .compare(a, b) <= 0
                                ? a
                                : b;
                        UUID loserId = winner == a ? bId : aId;
                        blockedRobots.add(loserId);
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
