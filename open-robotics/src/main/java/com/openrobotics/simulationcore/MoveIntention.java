package com.openrobotics.simulationcore;

import com.openrobotics.robot.Robot;
import com.openrobotics.map.Tile;

import java.util.UUID;

/** Represents a pending move request from a robot for one tick. */
public class MoveIntention {
    private final Robot robot;
    private final Tile from;
    private final Tile to;

    /**
     * Creates a move intention for the given robot.
     *
     * @param from the tile the robot is currently occupying
     * @param to the tile the robot intends to move to (may equal {@code from} for a wait)
     * @param robot the robot submitting this intention
     */
    public MoveIntention(Tile from, Tile to, Robot robot) {
        this.from = from;
        this.to = to;
        this.robot = robot;
    }

    public Robot getRobot() {
        return robot;
    }

    public UUID getRobotId() { return robot.getId(); }

    public Tile getFromTile() {
        return from;
    }

    public Tile getToTile() {
        return to;
    }
}
