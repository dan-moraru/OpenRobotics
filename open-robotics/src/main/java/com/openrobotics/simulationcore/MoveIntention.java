package com.openrobotics.simulationcore;

import com.openrobotics.robot.Robot;
import com.openrobotics.map.Tile;

import java.util.UUID;

/** represents a pending move request from a robot for one tick */
public class MoveIntention {
    private final Robot robot;
    private final Tile from;
    private final Tile to;

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
