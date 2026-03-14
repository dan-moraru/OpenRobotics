package com.openrobotics.simulationcore;

import com.openrobotics.map.Tile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Basic CS4 traffic-rules policy.
 *
 * <p>For the configured intersection tiles, at most one robot may enter a given
 * intersection tile during a tick. Robots are processed in deterministic robot-id order,
 * and later robots targeting an already-claimed intersection are converted to waits.</p>
 *
 * <p>This is the current minimal version of the policy. It does not yet model the
 * design document's richer queueing and priority behavior for intersections.</p>
 */
public class TrafficRulesPolicy implements CoordinationPolicy {
    private final Set<Tile> intersections;

    public TrafficRulesPolicy(Set<Tile> intersectionTiles) {
        // Copy input so policy state is not affected by later changes
        this.intersections = (intersectionTiles == null)
                ? new HashSet<>()
                : new HashSet<>(intersectionTiles);
    }

    @Override
    public MoveIntention[] apply(MoveIntention[] intentions) {
        // Normalize and sort once so policy outcomes are reproducible.
        MoveIntention[] ordered = CoordinationPolicy.orderedIntentions(intentions);
        List<MoveIntention> result = new ArrayList<>(ordered.length);
        // Track which intersection tiles have already been granted entry this tick.
        Set<Tile> claimedIntersections = new HashSet<>();

        for (MoveIntention intention : ordered) {
            // Ignore malformed intentions and stationary robots.
            if (!CoordinationPolicy.hasTiles(intention) || !CoordinationPolicy.isActualMove(intention)) {
                result.add(intention);
                continue;
            }

            Tile destination = intention.getToTile();
            // Non-intersection moves pass through unchanged.
            if (!CoordinationPolicy.containsTileByCoordinates(intersections, destination)) {
                result.add(intention);
                continue;
            }

            // Once an intersection tile is claimed for this tick, all later contenders wait.
            if (CoordinationPolicy.containsTileByCoordinates(claimedIntersections, destination)) {
                result.add(CoordinationPolicy.forceWait(intention));
            } else {
                CoordinationPolicy.addTileByCoordinates(claimedIntersections, destination);
                result.add(intention);
            }
        }

        return result.toArray(new MoveIntention[0]);
    }
}
