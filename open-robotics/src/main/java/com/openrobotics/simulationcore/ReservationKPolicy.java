package com.openrobotics.simulationcore;

import com.openrobotics.map.Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Basic CS5 reservation-k policy:
// reserves destination tiles per tick and forces conflicts to wait
// This first version works as k=1 (basically reserve the next tile only)
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
        // Reservations are per tick at least for now
        reservedTiles.clear();

        MoveIntention[] ordered = sortByRobotId(copyNonNull(intentions));
        List<MoveIntention> result = new ArrayList<>(ordered.length);

        for (MoveIntention intention : ordered) {
            if (!hasTiles(intention) || !isActualMove(intention)) {
                result.add(intention);
                continue;
            }

            // check whether the robot’s destination tile is a reserved tile
            Tile destination = intention.getToTile();
            if (containsTileByCoordinates(reservedTiles, destination)) {
                // if so, force wait
                result.add(forceWait(intention));
                continue;
            }

            // reserve only one tile for now (k=1).
            // other robots targeting the same tile in the same tick will be forced to wait
            if (k >= 1) {
                addTileByCoordinates(reservedTiles, destination);
            }
            // and keep the move intention as approved
            result.add(intention);
        }

        return result.toArray(new MoveIntention[0]);
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
        Arrays.sort(intentions, (a, b) -> Integer.compare(a.getRobotId(), b.getRobotId()));
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
                intention.getRobotId()
        );
    }

    // Coordinate-based checks
    // until Tile.equals is implemented
    private boolean containsTileByCoordinates(Set<Tile> tiles, Tile target) {
        for (Tile tile : tiles) {
            if (tile.getX() == target.getX() && tile.getY() == target.getY()) {
                return true;
            }
        }
        return false;
    }

    private void addTileByCoordinates(Set<Tile> tiles, Tile tileToAdd) {
        if (!containsTileByCoordinates(tiles, tileToAdd)) {
            tiles.add(tileToAdd);
        }
    }

    public int getK() {
        return this.k;
    }
}
