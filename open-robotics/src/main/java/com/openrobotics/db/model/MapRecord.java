package com.openrobotics.db.model;

import java.sql.Timestamp;
import java.util.UUID;

/** Database record for the {@code maps} table. */
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getWidth() { return width; }
    /**
     * Sets the map width.
     *
     * @param width the map width in tiles; must be positive
     * @throws IllegalArgumentException if {@code width} is zero or negative
     */
    public void setWidth(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        this.width = width;
    }

    public int getHeight() { return height; }
    /**
     * Sets the map height.
     *
     * @param height the map height in tiles; must be positive
     * @throws IllegalArgumentException if {@code height} is zero or negative
     */
    public void setHeight(int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        this.height = height;
    }

    public String getTileData() { return tileData; }
    public void setTileData(String tileData) { this.tileData = tileData; }

    public boolean isPreset() { return preset; }
    public void setPreset(boolean preset) { this.preset = preset; }

    public Integer getRandomSeed() { return randomSeed; }
    public void setRandomSeed(Integer randomSeed) { this.randomSeed = randomSeed; }

    public Timestamp getCreatedAt() { return createdAt == null ? null : new Timestamp(createdAt.getTime()); }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt == null ? null : new Timestamp(createdAt.getTime()); }
}
