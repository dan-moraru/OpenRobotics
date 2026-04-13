package com.openrobotics.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.openrobotics.common.Direction;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;

/** warehouse grid; owns all tiles and entities, and provides spatial queries (uml 3.3.3) */
public class Map {
    private UUID mapid; // unique identifier for database storage
    private final int width;  // number of columns (x-axis)
    private final int height; // number of rows (y-axis)
    private final Tile[][] grid; // grid[height][width] -> grid[row][col] -> grid[y][x]
    private final List<MapEntity> entities; // all objects placed on the map

    public Map(UUID mapId, int width, int height) {
        this(width, height);
        this.mapid = mapId;
    }

    public Map(int width, int height) {
        this.mapid = UUID.randomUUID();
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        this.width = width;
        this.height = height;
        // height rows, each with width columns
        this.grid = new Tile[height][width];
        this.entities = new ArrayList<>();

        // initialize all tiles; row = y, col = x
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                // Tile constructor takes (x, y) so col goes first
                grid[row][col] = new Tile(col, row);
            }
        }
    }

    public UUID getMapid() { return mapid; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /** returns the tile at grid position (x, y), or null if out of bounds */
    // internally we flip to grid[y][x] because the array is row-major
    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return grid[y][x];
    }

    /** true if (x, y) is in bounds and the tile is not currently occupied or blocked by a dead robot */
    public boolean isValidMove(int x, int y) {
        Tile tile = getTile(x, y);
        return tile != null && !tile.isOccupied() && !hasDeadRobotAt(tile.getPosition());
    }

    public void addEntity(MapEntity entity) {
        entities.add(entity);
        if (entity instanceof DeliveryStation || entity instanceof ChargingStation) {
            updateStationOverlapFlags(entity.getPosition());
        }
    }

    public boolean removeEntity(MapEntity entity) {
        boolean removed = entities.remove(entity);
        if (removed && (entity instanceof DeliveryStation || entity instanceof ChargingStation)) {
            updateStationOverlapFlags(entity.getPosition());
        }
        return removed;
    }

    // get all entities at a given position
    public List<MapEntity> getEntitiesAt(Vector2D pos) {
        List<MapEntity> result = new ArrayList<>();
        for (MapEntity entity : entities) {
            if (entity.getPosition().equals(pos)) {
                result.add(entity);
            }
        }
        return result;
    }

    public List<MapEntity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    /** semantic alias for getNeighbors(); prefer this name in task/dispatcher contexts */
    public List<Vector2D> getTraversableAdjacentTiles(Vector2D pos) {
        return getNeighbors(pos);
    }

    /** true if pos has at least one traversable cardinal neighbor */
    public boolean hasTraversableAdjacentTile(Vector2D pos) {
        return !getTraversableAdjacentTiles(pos).isEmpty();
    }

    /** true if a Rack entity occupies the given position */
    public boolean isRackAt(Vector2D pos) {
        for (MapEntity entity : entities) {
            if (entity instanceof Rack && entity.getPosition().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** true if pos is in bounds and holds no Rack, Obstacle, or dead robot; live robot conflicts are handled by CollisionManager */
    public boolean isTraversable(Vector2D pos) {
        Tile tile = getTile(pos.getX(), pos.getY());
        if (tile == null) return false;

        for (MapEntity entity : entities) {
            if (entity.getPosition().equals(pos)) {
                // Racks are solid. Robots interact with them from the side.
                if (entity instanceof Rack
                        || entity instanceof Obstacle
                        || (entity instanceof Robot robot && robot.getState() == RobotState.BATTERY_DEAD)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** traversable cardinal neighbors of pos in Direction-enum order for determinism */
    public List<Vector2D> getNeighbors(Vector2D pos) {
        List<Vector2D> neighbors = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            Vector2D neighbor = pos.add(dir.dx, dir.dy); // apply direction delta
            if (isTraversable(neighbor)) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    /** nearest ChargingStation to {@code from} by manhattan distance; null if none exist */
    public Vector2D findNearestChargingStation(Vector2D from) {
        Vector2D nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapEntity entity : entities) {
            // only chargingstation counts, not generic stations
            if (entity instanceof ChargingStation) {
                int dist = from.manhattanDistance(entity.getPosition());
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = entity.getPosition();
                }
            }
        }
        return nearest;
    }

    private boolean hasDeadRobotAt(Vector2D pos) {
        for (MapEntity entity : entities) {
            if (entity.getPosition().equals(pos)
                    && entity instanceof Robot robot
                    && robot.getState() == RobotState.BATTERY_DEAD) {
                return true;
            }
        }
        return false;
    }

    private void updateStationOverlapFlags(Vector2D pos) {
        Tile tile = getTile(pos.getX(), pos.getY());
        if (tile == null) {
            return;
        }

        boolean hasDeliveryStation = entities.stream()
                .anyMatch(entity -> entity instanceof DeliveryStation && entity.getPosition().equals(pos));
        boolean hasChargingStation = entities.stream()
                .anyMatch(entity -> entity instanceof ChargingStation && entity.getPosition().equals(pos));
        tile.setDeliveryStation(hasDeliveryStation);
        tile.setChargingStation(hasChargingStation);
    }
}
