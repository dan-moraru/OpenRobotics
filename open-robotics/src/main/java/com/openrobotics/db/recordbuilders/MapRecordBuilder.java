package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.MapRecord;
import com.openrobotics.map.Map;

/** Builds {@link MapRecord} instances from {@link Map} objects for database persistence. */
public class MapRecordBuilder {
    private MapRecord record;

    /**
     * Creates a builder pre-filled from the given map.
     *
     * @param map the warehouse map to build a record from
     */
    public MapRecordBuilder(Map map) {
        this.record = new MapRecord();
        record.setId(map.getMapid());
        record.setName("Map " + map.getMapid());
        record.setWidth(map.getWidth());
        record.setHeight(map.getHeight());

        // placeholder to satisfy not-null constraint
        record.setTileData("{}");

        record.setPreset(false);
    }

    /**
     * Returns the built {@link MapRecord}.
     *
     * @return the populated map record
     */
    public MapRecord build() {
        return record;
    }
}
