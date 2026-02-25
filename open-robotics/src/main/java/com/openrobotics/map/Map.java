package com.openrobotics.map;

import java.util.ArrayList;
import java.util.List;

// warehouse grid (uml 3.3.3)
public class Map {
    private final int width;  // number of columns (x-axis)
    private final int height; // number of rows (y-axis)
    private final Tile[][] grid; // grid[height][width] -> grid[row][col] -> grid[y][x]
    private final List<MapEntity> entities; // all objects placed on the map

    public Map(int width, int height) {
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

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    // get tile at (x, y); callers pass natural (x, y) order
    // internally we flip to grid[y][x] because the array is row-major
    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return grid[y][x];
    }

    // check if a move to (x, y) is valid: in bounds and not occupied
    public boolean isValidMove(int x, int y) {
        Tile tile = getTile(x, y);
        return tile != null && !tile.isOccupied();
    }

    // add a map entity to the entity list
    public void addEntity(MapEntity entity) {
        entities.add(entity);
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
        return entities;
    }
}
