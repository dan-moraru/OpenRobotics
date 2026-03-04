package com.openrobotics.simulationcore;

import com.openrobotics.*;
import com.openrobotics.map.Tile;

// Represents a move intention for a robot
public class MoveIntention {
    private final int robotId;
    private final Tile from; // Current tile robot is intending to move away from
    private final Tile to; // Tile robot is intended to move on

    public MoveIntention(Tile from, Tile to, int robotId) {
        this.from = from;
        this.to = to;
        this.robotId = robotId;
    }

    public int getRobotId() {
        return robotId;
    }

    public Tile getFromTile() {
        return from;
    }

    public Tile getToTile() {
        return to;
    }
}
