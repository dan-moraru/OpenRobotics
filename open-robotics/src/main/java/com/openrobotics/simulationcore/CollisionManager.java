package com.openrobotics.simulationcore;

import com.openrobotics.Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollisionManager {

    // legal checks:
    // - no null intention/from/to
    // - no negative robot id
    // - one tile per tick max by Manhattan distance
    //   and distance 0 means wait/non move, which is fine
    public boolean isLegalIntention(MoveIntention intention) {
        if (intention == null || intention.getFromTile() == null || intention.getToTile() == null) {
            return false;
        }
        if (intention.getRobotId() < 0) {
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

    // Used by the engine to keep only conflict-free intentions.
    public MoveIntention[] resolveConflicts(MoveIntention[] intentions) {
        if (intentions == null || intentions.length == 0) {
            return new MoveIntention[0];
        }

        // ======= Step 1: keep one legal intention per robot. =======

        // This keeps a table of at most one MoveIntention per robot for current tick.
        Map<Integer, MoveIntention> uniqueByRobotId = new HashMap<>();

        // Unduplicate any duplicate intentions
        for (MoveIntention intention : intentions) {
            if (isLegalIntention(intention) &&
                    !uniqueByRobotId.containsKey(intention.getRobotId())) {

                uniqueByRobotId.put(intention.getRobotId(), intention);
            }
        }

        // Convert the hashmap into an array
        MoveIntention[] candidates = uniqueByRobotId.values().toArray(new MoveIntention[0]);
        // Sort it by ascending robot id
        Arrays.sort(candidates, (a, b) -> Integer.compare(a.getRobotId(), b.getRobotId()));

        // ===== Step 2: same-target conflicts =======

        // Blocked robots are forced to wait for this tick.
        Set<Integer> blockedRobots = new HashSet<>();

        // group intentions by target tile
        Map<String, List<MoveIntention>> byDestination = new HashMap<>();

        for (MoveIntention intention : candidates) {
            byDestination.computeIfAbsent(tileKey(intention.getToTile()), ignored -> new ArrayList<>())
                    .add(intention);
        }

        // Now go through each destination tile group of intentions
        for (List<MoveIntention> group : byDestination.values()) {
            if (group.size() <= 1) { // No conflicts with groups sizes of 1 or less
                continue;
            }

            int winnerId = Integer.MAX_VALUE;
            for (MoveIntention intention : group) {
                // The lowest robot id wins, all others are blocked.
                winnerId = Math.min(winnerId, intention.getRobotId());
            }

            for (MoveIntention intention : group) {
                if (intention.getRobotId() != winnerId) {
                    blockedRobots.add(intention.getRobotId());
                }
            }
        }

        // ====== Step 3: swap conflicts (A moves to B's tile while B moves to A's tile). ==
        // Rule: lower robot id wins, higher id waits.
        for (int i = 0; i < candidates.length; i++) {
            MoveIntention a = candidates[i];

            // Ignore blocked robots and robots that won't move
            if (blockedRobots.contains(a.getRobotId()) || !isActualMove(a)) {
                continue;
            }

            // Now check over all other robot intentions
            for (int j = i + 1; j < candidates.length; j++) {
                MoveIntention b = candidates[j];
                if (blockedRobots.contains(b.getRobotId()) || !isActualMove(b)) {
                    continue;
                }

                boolean isSwap = sameTile(a.getToTile(), b.getFromTile())
                        && sameTile(b.getToTile(), a.getFromTile());

                if (isSwap) {
                    int loserId = Math.max(a.getRobotId(), b.getRobotId());
                    blockedRobots.add(loserId);
                }
            }
        }

        // ======= Step 4: return approved intentions ========
        List<MoveIntention> approved = new ArrayList<>();

        for (MoveIntention intention : candidates) {
            if (!blockedRobots.contains(intention.getRobotId())) {
                approved.add(intention);
            }
        }

        return approved.toArray(new MoveIntention[0]);
    }
}
