package com.openrobotics.simulationcore;

import com.openrobotics.Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Basic CS4 traffic-rules policy:
// for configured intersection tiles, allow only one robot to enter per tick
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

        MoveIntention[] ordered = sortByRobotId(copyNonNull(intentions));
        // output list
        List<MoveIntention> result = new ArrayList<>(ordered.length);
        // need to track which tiles are "won" by robots already
        Set<Tile> claimedIntersections = new HashSet<>();

        for (MoveIntention intention : ordered) {
            if (!hasTiles(intention) || !isActualMove(intention)) {
                result.add(intention);
                continue;
            }

            // check whether the robot’s destination tile is an intersection tile
            Tile destination = intention.getToTile();
            if (!containsTileByCoordinates(intersections, destination)) {
                // if not, traffic rules don't apply
                result.add(intention);
                continue;
            }

            // if destination is already in claimedIntersections, then
            // another robot already won that intersection this tick.
            if (containsTileByCoordinates(claimedIntersections, destination)) {
                // loser robot is forced to wait
                result.add(forceWait(intention));
            }
            else {
                // Otherwise robot claims that intersection and keeps its move
                addTileByCoordinates(claimedIntersections, destination);
                result.add(intention);
            }
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
        Arrays.sort(intentions, (a, b) -> a.getRobot().getId().compareTo(b.getRobot().getId()));
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
}
