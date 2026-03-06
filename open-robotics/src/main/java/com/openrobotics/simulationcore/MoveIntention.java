package com.openrobotics.simulationcore;

import com.openrobotics.*;

/**
 * Represents a move intention for a robot
 */
public class MoveIntention {
    private final Robot robot;
    private final Tile from;
    private final Tile to;

    /**
     * Constructs a new MoveIntention object
     *
     * @param from the tile this move intention is going to
     * @param to the tile this move intention is going from
     * @param robot the robot this move intention belongs to
     */
    public MoveIntention(Tile from, Tile to, Robot robot) {
        this.from = from;
        this.to = to;
        this.robot = robot;
    }

    public Robot getRobot() {
        return robot;
    }

    public Tile getFromTile() {
        return from;
    }

    public Tile getToTile() {
        return to;
    }
}
