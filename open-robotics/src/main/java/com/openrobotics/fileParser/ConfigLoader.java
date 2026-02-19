package com.openrobotics.fileParser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T load(String path, Class<T> clazz) throws IOException {
        return mapper.readValue(new File(path), clazz);
    }

    public static void save(String path, Object obj) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), obj);
    }

    public static void main(String[] args) {
        try {
            MockSimulation simulation = ConfigLoader.load("./open-robotics/src/main/java/com/openrobotics/fileparser/test.json", MockSimulation.class);

            System.out.println("Map size: " + simulation.width + "x" + simulation.height);

            for (MockRobot r : simulation.robots) {
                System.out.println("Robot: " + r.id + " at (" + r.x + ", " + r.y + ")");
            }

            ConfigLoader.save("./open-robotics/src/main/java/com/openrobotics/fileparser/output.json", simulation);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    public static void validate(SimulationConfig config) {
//
//        if (config.version != 1) {
//            throw new IllegalArgumentException("Unsupported config version");
//        }
//
//        if ("RESERVATION_K".equals(config.coordinationPolicy.type)
//                && config.coordinationPolicy.k == null) {
//            throw new IllegalArgumentException("k required for RESERVATION_K");
//        }
//
//        if ("SPAWN_RATE".equals(config.workload.mode)
//                && config.workload.spawnRatePerMin == null) {
//            throw new IllegalArgumentException("spawnRatePerMin required for SPAWN_RATE");
//        }
//    }
}

