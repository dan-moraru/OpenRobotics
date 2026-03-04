package com.openrobotics.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

/**
 * Utility class for parsing JSON files using Jackson library.
 * Loads config files as JSON to set up the simulation objects.
 * Saves the simulation state and all objects into a new JSON file.
 */
public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ConfigLoader() {} // Prevent instantiation

    public static <T> T load(String path, Class<T> clazz) throws IOException {
        return mapper.readValue(new File(path), clazz);
    }

    public static void save(String path, Object obj) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), obj);
    }
}
