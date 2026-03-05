package com.openrobotics.simulationcore;

import com.openrobotics.Robot;
import com.openrobotics.Tile;

import java.util.*;

public class CollisionManager {

    // legal checks:
    // - no null intention/from/to
    // - no negative robot id
    // - one tile per tick max by Manhattan distance
    //   and distance 0 means wait/non move, which is fine
    public boolean isLegalIntention(MoveIntention intention) {
        if (intention == null ||
                intention.getRobot() == null ||
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

    // Used by the engine to keep only conflict-free intentions.
    public MoveIntention[] resolveConflicts(MoveIntention[] intentions) {
        if (intentions == null || intentions.length == 0) {
            return new MoveIntention[0];
        }

        // ===== Step 1: keep one legal intention per robot =====

        Map<UUID, MoveIntention> uniqueByRobot = new HashMap<>();

        for (MoveIntention intention : intentions) {
            if (!isLegalIntention(intention)) {
                continue;
            }

            Robot robot = intention.getRobot();
            UUID robotId = robot.getId();

            uniqueByRobot.putIfAbsent(robotId, intention);
        }

        MoveIntention[] candidates =
                uniqueByRobot.values().toArray(new MoveIntention[0]);

        // deterministic ordering by UUID
        Arrays.sort(candidates, Comparator.comparing(
                i -> i.getRobot().getId().toString()
        ));

        // ===== Step 2: same-target conflicts =====

        Set<UUID> blockedRobots = new HashSet<>();

        Map<String, List<MoveIntention>> byDestination = new HashMap<>();

        for (MoveIntention intention : candidates) {
            byDestination
                    .computeIfAbsent(tileKey(intention.getToTile()), k -> new ArrayList<>())
                    .add(intention);
        }

        for (List<MoveIntention> group : byDestination.values()) {

            if (group.size() <= 1) {
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
        }

        // ===== Step 3: swap conflicts =====

        for (int i = 0; i < candidates.length; i++) {
            MoveIntention a = candidates[i];
            UUID aId = a.getRobot().getId();

            if (blockedRobots.contains(aId) || !isActualMove(a)) {
                continue;
            }

            for (int j = i + 1; j < candidates.length; j++) {
                MoveIntention b = candidates[j];
                UUID bId = b.getRobot().getId();

                if (blockedRobots.contains(bId) || !isActualMove(b)) {
                    continue;
                }

                boolean isSwap =
                        sameTile(a.getToTile(), b.getFromTile()) &&
                                sameTile(b.getToTile(), a.getFromTile());

                if (isSwap) {

                    UUID loser = aId.toString().compareTo(bId.toString()) > 0
                            ? aId
                            : bId;

                    blockedRobots.add(loser);
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
