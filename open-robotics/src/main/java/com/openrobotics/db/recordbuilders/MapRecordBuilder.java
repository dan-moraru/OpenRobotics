package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.MapRecord;
import com.openrobotics.map.Map;

public class MapRecordBuilder {
    private MapRecord record;

    public MapRecordBuilder(Map map) {
        this.record = new MapRecord();
        record.setId(map.getMapid());
        record.setName("Map " + map.getMapid());
        record.setWidth(map.getWidth());
        record.setHeight(map.getHeight());

        // Placeholder for now to avoid issues with not null constraint.
        // TODO: Rename tile_data to entities_list or something similar
        // TODO: Serialize the map's entities into a JSON string and store it in this field
        record.setTileData("{}");

        /* TODO: Implement code to allow for differentiation between preset maps and user-created maps
            and set this field accordingly */
        record.setPreset(false);
    }

    public MapRecord build() {
        return record;
    }
}
