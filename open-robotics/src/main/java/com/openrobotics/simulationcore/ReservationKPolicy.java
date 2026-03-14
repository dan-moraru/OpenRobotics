package com.openrobotics.simulationcore;

import com.openrobotics.map.Tile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Basic CS5 reservation-k policy.
 *
 * <p>The current implementation is intentionally conservative: it only reserves the
 * immediate destination tile for the current tick and forces later contenders to wait.
 * In practice this behaves like {@code k = 1}, even if a larger value is configured.</p>
 *
 * <p>This means the policy is wired for future expansion, but it does not yet reserve
 * the next {@code k} tiles along a robot path as described in the design document.</p>
 */
public class ReservationKPolicy implements CoordinationPolicy {
    private final int k;
    private final Set<Tile> reservedTiles;

    public ReservationKPolicy(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        this.k = k;
        this.reservedTiles = new HashSet<>();
    }

    @Override
    public MoveIntention[] apply(MoveIntention[] intentions) {
        // Reservations are recomputed every tick from the current set of intentions.
        reservedTiles.clear();

        // Deterministic ordering ensures repeatable winners when robots contend.
        MoveIntention[] ordered = CoordinationPolicy.orderedIntentions(intentions);
        List<MoveIntention> result = new ArrayList<>(ordered.length);

        for (MoveIntention intention : ordered) {
            // Ignore malformed intentions and stationary robots.
            if (!CoordinationPolicy.hasTiles(intention) || !CoordinationPolicy.isActualMove(intention)) {
                result.add(intention);
                continue;
            }

            Tile destination = intention.getToTile();
            // If another robot already reserved this destination for the tick, this robot waits.
            if (CoordinationPolicy.containsTileByCoordinates(reservedTiles, destination)) {
                result.add(CoordinationPolicy.forceWait(intention));
                continue;
            }

            // Current behavior reserves only the next tile, not a multi-step path segment.
            if (k >= 1) {
                CoordinationPolicy.addTileByCoordinates(reservedTiles, destination);
            }
            result.add(intention);
        }

        return result.toArray(new MoveIntention[0]);
    }

    public int getK() {
        return this.k;
    }
}
