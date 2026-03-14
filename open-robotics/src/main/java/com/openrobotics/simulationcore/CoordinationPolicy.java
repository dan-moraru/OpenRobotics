package com.openrobotics.simulationcore;

import com.openrobotics.map.Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Applies coordination rules to a tick's movement intentions before collision resolution.
 */
public interface CoordinationPolicy {
    /**
     * Applies policy rules to a tick's intentions.
     * Policies force a robot to wait by returning an intention from its current tile to itself.
     *
     * @param intentions the raw intentions collected for the current tick
     * @return one filtered intention per participating robot in deterministic order
     */
    MoveIntention[] apply(MoveIntention[] intentions);

    /**
     * Returns a policy that preserves the current intentions.
     *
     * @return a no-op coordination policy
     */
    static CoordinationPolicy noOp() {
        return CoordinationPolicy::copyNonNull;
    }

    /**
     * Returns a new array without null intentions.
     *
     * @param intentions the input intentions
     * @return a compacted array containing only non-null intentions
     */
    static MoveIntention[] copyNonNull(MoveIntention[] intentions) {
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

    /**
     * Returns a sorted copy of the provided intentions using robot id ordering.
     *
     * @param intentions the input intentions
     * @return a deterministically ordered copy
     */
    static MoveIntention[] orderedIntentions(MoveIntention[] intentions) {
        MoveIntention[] ordered = copyNonNull(intentions);
        Arrays.sort(ordered, (a, b) -> a.getRobot().getId().compareTo(b.getRobot().getId()));
        return ordered;
    }

    /**
     * Returns true when the intention includes both source and destination tiles.
     *
     * @param intention the intention to inspect
     * @return true when both tiles are present
     */
    static boolean hasTiles(MoveIntention intention) {
        return intention != null
                && intention.getFromTile() != null
                && intention.getToTile() != null;
    }

    /**
     * Returns true when the intention moves to a different tile.
     *
     * @param intention the intention to inspect
     * @return true when the robot changes coordinates
     */
    static boolean isActualMove(MoveIntention intention) {
        return hasTiles(intention) && !sameTileCoordinates(intention.getFromTile(), intention.getToTile());
    }

    /**
     * Converts a move into an explicit wait on the robot's current tile.
     *
     * @param intention the original intention
     * @return a wait intention for the same robot
     */
    static MoveIntention forceWait(MoveIntention intention) {
        return new MoveIntention(
                intention.getFromTile(),
                intention.getFromTile(),
                intention.getRobot()
        );
    }

    /**
     * Compares tiles by coordinates.
     *
     * @param a the first tile
     * @param b the second tile
     * @return true when both tiles represent the same coordinates
     */
    static boolean sameTileCoordinates(Tile a, Tile b) {
        return a != null
                && b != null
                && a.getX() == b.getX()
                && a.getY() == b.getY();
    }

    /**
     * Returns true when the set contains a tile with the same coordinates as the target.
     *
     * @param tiles the tiles to inspect
     * @param target the tile being searched for
     * @return true when a matching coordinate pair exists
     */
    static boolean containsTileByCoordinates(Set<Tile> tiles, Tile target) {
        if (tiles == null || target == null) {
            return false;
        }

        for (Tile tile : tiles) {
            if (sameTileCoordinates(tile, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a tile to the set if another tile with the same coordinates is not already present.
     *
     * @param tiles the destination set
     * @param tileToAdd the tile to add
     */
    static void addTileByCoordinates(Set<Tile> tiles, Tile tileToAdd) {
        if (tiles == null || tileToAdd == null) {
            return;
        }

        if (!containsTileByCoordinates(tiles, tileToAdd)) {
            tiles.add(tileToAdd);
        }
    }
}
