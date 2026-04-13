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
    private static final String BASE_DIR_PROPERTY = "openrobotics.config.baseDir";

    private ConfigLoader() {}

    private static List<Path> allowedBaseDirs() throws IOException {
        List<Path> bases = new ArrayList<>();
        String configuredBase = System.getProperty(BASE_DIR_PROPERTY);
        if (configuredBase != null && !configuredBase.isBlank()) {
            bases.add(new File(configuredBase).getCanonicalFile().toPath());
        } else {
            bases.add(new File(System.getProperty("user.dir", ".")).getCanonicalFile().toPath());
        }
        bases.add(new File(System.getProperty("java.io.tmpdir", ".")).getCanonicalFile().toPath());
        bases.add(new File(System.getProperty("user.home", ".") + File.separator
            + ".open-robotics" + File.separator + "configs").getCanonicalFile().toPath());
        return bases;
    }

    private static File validatePath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be null or blank");
        }
        File file = new File(path).getCanonicalFile();
        Path filePath = file.toPath();
        for (Path base : allowedBaseDirs()) {
            if (filePath.startsWith(base)) {
                return file;
            }
        }
        throw new SecurityException("path is outside allowed config directories: " + file);
    }

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
