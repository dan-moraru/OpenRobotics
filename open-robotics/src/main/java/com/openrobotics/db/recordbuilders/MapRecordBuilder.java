package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.MapRecord;
import com.openrobotics.map.Map;

/** builds MapRecord instances from Map objects for database persistence */
public class MapRecordBuilder {
    private MapRecord record;

    public MapRecordBuilder(Map map) {
        this.record = new MapRecord();
        record.setId(map.getMapid());
        record.setName("Map " + map.getMapid());
        record.setWidth(map.getWidth());
        record.setHeight(map.getHeight());

        // placeholder to satisfy not-null constraint; TODO: serialize map entities to JSON
        record.setTileData("{}");

        // TODO: differentiate preset maps from user-created maps and set accordingly
        record.setPreset(false);
    }

    public MapRecord build() {
        return record;
    }
}
