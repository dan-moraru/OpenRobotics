package com.openrobotics.robot.navigation;

import com.openrobotics.common.Direction;
import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.MoveIntention;

import java.util.Random;

/** random navigation strategy; used in tests only, not wired in production */
public class RandomNavigation implements NavigationStrategy {

    /** shuffles all four cardinal directions with fisher-yates, skips any that land on rerouteAvoidTile, and returns the first valid move; falls back to WAIT if all are blocked */
    @Override
    public MoveIntention getNextMove(Robot robot, Map map) {
        Vector2D position = robot.getPosition();
        var fromTile = map.getTile(position.getX(), position.getY());
        Vector2D target = robot.getTarget();
        Vector2D rerouteAvoidTile = robot.getRerouteAvoidTile();

        // Try each direction at most once in random order, then fall back to WAIT.
        Direction[] directions = Direction.values();
        // unseeded — intentionally non-deterministic; this strategy is test-only
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
            Vector2D next = new Vector2D(newX, newY);

            if (rerouteAvoidTile != null && !rerouteAvoidTile.equals(target) && rerouteAvoidTile.equals(next)) {
                continue;
            }

            if (map.isValidMove(newX, newY)) {
                return new MoveIntention(fromTile, map.getTile(newX, newY), robot);
            }
        }

        return new MoveIntention(fromTile, fromTile, robot);
    }

    @Override
    public String toString() {
        return "RANDOM";
    }
}
