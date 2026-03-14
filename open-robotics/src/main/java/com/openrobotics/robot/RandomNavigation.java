package com.openrobotics.robot;

import com.openrobotics.common.Direction;
import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.simulationcore.MoveIntention;

import java.util.Random;

/**
 * Represents a random navigation strategy where the robot chooses a random direction to move in
 */
public class RandomNavigation implements NavigationStrategy {

    /**
     * Generates a move intention for a given robot based on the RandomNavigation strategy. In this strategy,
     * the robot chooses a random direction (up, down, left, or right) and makes the intention to move to it
     * if it is a valid move.
     * @param robot the robot that a move intention is being generated for
     * @param map the map of the warehouse environment
     * @return a MoveIntention representing the choice for the robots next move
     */
    @Override
    public MoveIntention getNextMove(Robot robot, Map map) {
        Vector2D position = robot.getPosition();
        var fromTile = map.getTile(position.getX(), position.getY());

        // Try each direction at most once in random order, then fall back to WAIT.
        Direction[] directions = Direction.values();
        Random random = new Random();
        for (int i = directions.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Direction tmp = directions[i];
            directions[i] = directions[j];
            directions[j] = tmp;
        }

        for (Direction randomDirection : directions) {
            int newX = position.getX() + randomDirection.dx;
            int newY = position.getY() + randomDirection.dy;

            if (map.isValidMove(newX, newY)) {
                return new MoveIntention(fromTile, map.getTile(newX, newY), robot);
            }
        }

        return new MoveIntention(fromTile, fromTile, robot);
    }
}
