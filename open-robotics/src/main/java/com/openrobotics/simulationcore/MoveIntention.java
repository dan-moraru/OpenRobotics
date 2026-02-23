package com.openrobotics.simulationcore;

import com.openrobotics.*;

// Represents a move intention for a robot
public class MoveIntention {
    int robotId;
    Tile from; // Current tile robot is intending to move away from
    Tile to; // Tile robot is intended to move on

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
