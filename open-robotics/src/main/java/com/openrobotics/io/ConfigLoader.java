package com.openrobotics.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

/**
 * Loads and saves simulation config JSON files while canonicalizing file paths and restricting
 * deserialization targets.
 */
public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ConfigLoader() {}

    /**
     * Resolves a config path to a canonical file.
     *
     * @param path the user-supplied file path
     * @return the canonical file for {@code path}
     * @throws IOException if canonicalization fails
     * @throws IllegalArgumentException if {@code path} is {@code null} or blank
     */
    private static File validatePath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be null or blank");
        }
        // Resolves the file and removes any relative navigation
        return new File(path).getCanonicalFile();
    }

    /**
     * Validates that a deserialization target class is in an allowed OpenRobotics package.
     *
     * @param <T> the deserialization target type
     * @param clazz the target class
     * @throws IllegalArgumentException if {@code clazz} is {@code null}
     * @throws SecurityException if {@code clazz} is outside the allowed packages
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
     * Loads a JSON file at {@code path} into an instance of {@code clazz}.
     *
     * @param <T> the deserialized type
     * @param path the JSON file path
     * @param clazz the target class
     * @return the deserialized object
     * @throws IOException if the file cannot be read or deserialized
     * @throws IllegalArgumentException if {@code path} is blank or {@code clazz} is {@code null}
     * @throws SecurityException if {@code clazz} is outside the allowed packages
     */
    public static <T> T load(String path, Class<T> clazz) throws IOException {
        validateTargetClass(clazz);
        File file = validatePath(path);
        JsonNode root = mapper.readTree(file);
        return mapper.treeToValue(root, clazz);
    }

    /**
     * Serializes {@code obj} as pretty-printed JSON and writes it to {@code path}.
     *
     * @param path the output file path
     * @param obj the object to serialize
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if {@code path} is blank or {@code obj} is {@code null}
     */
    public static void save(String path, Object obj) throws IOException {
        if (obj == null) {
            throw new IllegalArgumentException("obj must not be null");
        }
        File file = validatePath(path);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, obj);
    }
}
