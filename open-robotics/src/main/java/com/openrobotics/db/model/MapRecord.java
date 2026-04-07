package com.openrobotics.db.model;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a map record in the database.
 */
public class MapRecord {

    private UUID id;
    private String name;
    private int width;
    private int height;
    private String tileData;
    private boolean preset;
    private Integer randomSeed;
    private Timestamp createdAt;

    public MapRecord() {}

    /**
     * Gets the ID of the map.
     * @return ID of the map
     */
    public UUID getId() { 
        return id; 
    }

    /**
     * Sets the ID of the map.
     * @param id ID of the map
     */
    public void setId(UUID id) { 
        this.id = id; 
    }

    /**
     * Gets the name of the map.
     * @return Name of the map
     */
    public String getName() { 
        return name; 
    }

    /**
     * Sets the name of the map.
     * @param name Name of the map
     */
    public void setName(String name) { 
        this.name = name; 
    }

    /**
     * Gets the width of the map.
     * @return Width of the map
     */
    public int getWidth() { return width; }

    /**
     * Sets the width of the map.
     * @param width Width of the map
     */
    public void setWidth(int width) { 
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        this.width = width; 
    }

    /**
     * Gets the height of the map.
     * @return Height of the map
     */
    public int getHeight() { 
        return height; 
    }

    /**
     * Sets the height of the map.
     * @param height Height of the map
     */
    public void setHeight(int height) { 
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        this.height = height; 
    }

    /**
     * Gets the tile data of the map.
     * @return Tile data of the map
     */
    public String getTileData() { 
        return tileData;
    }

    /**
     * Sets the tile data of the map.
     * @param tileData Tile data of the map
     */
    public void setTileData(String tileData) { 
        this.tileData = tileData;
    }

    /**
     * Gets whether the map is a preset.
     * @return Whether the map is a preset
     */
    public boolean isPreset() { 
        return preset; 
    }

    /**
     * Sets whether the map is a preset.
     * @param preset Whether the map is a preset
     */
    public void setPreset(boolean preset) { 
        this.preset = preset; 
    }

    /**
     * Gets the random seed of the map.
     * @return Random seed of the map
     */
    public Integer getRandomSeed() { 
        return randomSeed; 
    }

    /**
     * Sets the random seed of the map.
     * @param randomSeed Random seed of the map
     */
    public void setRandomSeed(Integer randomSeed) { 
        this.randomSeed = randomSeed; 
    }

    /**
     * Gets the created at timestamp of the map.
     * @return Created at timestamp of the map
     */
    public Timestamp getCreatedAt() { 
        return createdAt == null ? null : new Timestamp(createdAt.getTime()); 
    }

    /**
     * Sets the created at timestamp of the map.
     * @param createdAt Created at timestamp of the map
     */
    public void setCreatedAt(Timestamp createdAt) { 
        this.createdAt = createdAt == null ? null : new Timestamp(createdAt.getTime()); 
    }
}
