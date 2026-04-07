package com.openrobotics.simulationcore;

import com.openrobotics.robot.Robot;
import com.openrobotics.map.Tile;

import java.util.Objects;
import java.util.UUID;

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
     * @param from the tile this move intention is going from
     * @param to the tile this move intention is going to
     * @param robot the robot this move intention belongs to
     */
    public MoveIntention(Tile from, Tile to, Robot robot) {
        this.from = Objects.requireNonNull(from, "from must not be null");
        this.to = Objects.requireNonNull(to, "to must not be null");
        this.robot = Objects.requireNonNull(robot, "robot must not be null");
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
