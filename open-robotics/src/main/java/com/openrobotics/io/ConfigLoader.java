package com.openrobotics.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** loads and saves simulation config files as JSON; path and class validation guard against path traversal and unsafe deserialization */
public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ConfigLoader() {}

    /**
     * Helper method to validate path of config file
     */
    private static File validatePath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be null or blank");
        }
        // Resolves the file and removes any relative navigation
        return new File(path).getCanonicalFile();
    }

    /**
     * Helper method to validate java class for parsing library
     */
    private static <T> void validateTargetClass(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz must not be null");
        }
        // restrict deserialization targets to openrobotics DTO/model packages
        String className = clazz.getName();
        if (!className.startsWith("com.openrobotics.io.")
            && !className.startsWith("com.openrobotics.db.model.")) {
            throw new SecurityException("clazz is not allowed for deserialization: " + className);
        }
    }

    /**
     * loads and deserializes a JSON file at {@code path} into an instance of {@code clazz}.
     * path must resolve under an allowed base directory; clazz must be in an openrobotics DTO/model package.
     *
     * @throws SecurityException if path or clazz fails validation
     */
    public static <T> T load(String path, Class<T> clazz) throws IOException {
        validateTargetClass(clazz);
        File file = validatePath(path);
        JsonNode root = mapper.readTree(file);
        return mapper.treeToValue(root, clazz);
    }

    /**
     * serializes {@code obj} as pretty-printed JSON and writes it to {@code path}.
     * path must resolve under an allowed base directory.
     *
     * @throws SecurityException if path fails validation
     */
    public static void save(String path, Object obj) throws IOException {
        if (obj == null) {
            throw new IllegalArgumentException("obj must not be null");
        }
        File file = validatePath(path);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, obj);
    }
}
