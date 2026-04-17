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

/** Warehouse grid; owns all tiles and entities and provides spatial queries. */
public class Map {
    private UUID mapid; // unique identifier for database storage
    private final int width;  // number of columns (x-axis)
    private final int height; // number of rows (y-axis)
    private final Tile[][] grid; // grid[height][width] -> grid[row][col] -> grid[y][x]
    private final List<MapEntity> entities; // all objects placed on the map

    /**
     * Creates a map with the given dimensions and assigns a specific database ID.
     *
     * @param mapId the UUID to assign to this map for database storage
     * @param width the number of columns
     * @param height the number of rows
     */
    public Map(UUID mapId, int width, int height) {
        this(width, height);
        this.mapid = mapId;
    }

    /**
     * Creates a map with the given dimensions and a randomly generated ID.
     *
     * @param width the number of columns
     * @param height the number of rows
     * @throws IllegalArgumentException if width or height is less than 1
     */
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

    /**
     * Returns the tile at grid position (x, y), or {@code null} if out of bounds.
     *
     * @param x the column index
     * @param y the row index
     * @return the tile at (x, y), or {@code null} if the position is out of bounds
     */
    public Tile getTile(int x, int y) {
        // internally we flip to grid[y][x] because the array is row-major
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return grid[y][x];
    }

    /**
     * Returns true if (x, y) is in bounds and the tile is not occupied or blocked by a dead robot.
     *
     * @param x the column index
     * @param y the row index
     * @return {@code true} if the position is a valid move target
     */
    public boolean isValidMove(int x, int y) {
        Tile tile = getTile(x, y);
        return tile != null && !tile.isOccupied() && !hasDeadRobotAt(tile.getPosition());
    }

    /**
     * Adds an entity to the map and updates station-overlap flags if it is a station.
     *
     * @param entity the entity to add
     */
    public void addEntity(MapEntity entity) {
        entities.add(entity);
        if (entity instanceof DeliveryStation || entity instanceof ChargingStation) {
            updateStationOverlapFlags(entity.getPosition());
        }
    }

    /**
     * Removes an entity from the map and updates station-overlap flags if it was a station.
     *
     * @param entity the entity to remove
     * @return {@code true} if the entity was present and has been removed
     */
    public boolean removeEntity(MapEntity entity) {
        boolean removed = entities.remove(entity);
        if (removed && (entity instanceof DeliveryStation || entity instanceof ChargingStation)) {
            updateStationOverlapFlags(entity.getPosition());
        }
        return removed;
    }

    /**
     * Returns all entities whose position equals {@code pos}.
     *
     * @param pos the grid position to query
     * @return a list of entities at the given position; empty if none
     */
    public List<MapEntity> getEntitiesAt(Vector2D pos) {
        List<MapEntity> result = new ArrayList<>();
        for (MapEntity entity : entities) {
            if (entity.getPosition().equals(pos)) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Returns an unmodifiable view of all entities on the map.
     *
     * @return an unmodifiable list of all map entities
     */
    public List<MapEntity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    /**
     * Returns the traversable cardinal neighbours of {@code pos}; semantic alias for
     * {@link #getNeighbors(Vector2D)}, preferred in task and dispatcher contexts.
     *
     * @param pos the grid position to query
     * @return a list of traversable neighbouring positions
     */
    public List<Vector2D> getTraversableAdjacentTiles(Vector2D pos) {
        return getNeighbors(pos);
    }

    /**
     * Returns true if {@code pos} has at least one traversable cardinal neighbour.
     *
     * @param pos the grid position to check
     * @return {@code true} if at least one neighbour of {@code pos} is traversable
     */
    public boolean hasTraversableAdjacentTile(Vector2D pos) {
        return !getTraversableAdjacentTiles(pos).isEmpty();
    }

    /**
     * Returns true if a {@link Rack} entity occupies the given position.
     *
     * @param pos the grid position to check
     * @return {@code true} if a rack is present at {@code pos}
     */
    public boolean isRackAt(Vector2D pos) {
        for (MapEntity entity : entities) {
            if (entity instanceof Rack && entity.getPosition().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if {@code pos} is in bounds and holds no rack, obstacle, or dead robot.
     * Live robot conflicts are handled separately by the collision manager.
     *
     * @param pos the grid position to check
     * @return {@code true} if a robot may move to this position
     */
    public boolean isTraversable(Vector2D pos) {
        Tile tile = getTile(pos.getX(), pos.getY());
        if (tile == null) return false;

        for (MapEntity entity : entities) {
            if (entity.getPosition().equals(pos)) {
                // racks are solid; robots interact with them from the side
                if (entity instanceof Rack
                        || entity instanceof Obstacle
                        || (entity instanceof Robot robot && robot.getState() == RobotState.BATTERY_DEAD)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the traversable cardinal neighbours of {@code pos} in Direction-enum order for determinism.
     *
     * @param pos the grid position to query
     * @return a list of traversable neighbouring positions
     */
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

    /**
     * Returns the position of the nearest {@link ChargingStation} to {@code from} by Manhattan
     * distance, or {@code null} if no charging stations exist on the map.
     *
     * @param from the position to measure from
     * @return the position of the nearest charging station, or {@code null} if none exist
     */
    public Vector2D findNearestChargingStation(Vector2D from) {
        Vector2D nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapEntity entity : entities) {
            // only ChargingStation counts, not generic stations
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
