package com.openrobotics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T load(String path, Class<T> clazz) throws IOException {
        return mapper.readValue(new File(path), clazz);
    }

    public static void main(String[] args) {
        try {
            MockSimulation simulation = ConfigLoader.load("D:\\Projects\\OpenRobotics\\open-robotics\\src\\main\\java\\com\\openrobotics\\config\\test.json", MockSimulation.class);

            System.out.println("Map size: " + simulation.width + "x" + simulation.height);

            for (MockRobot r : simulation.robots) {
                System.out.println("Robot: " + r.id + " at (" + r.x + ", " + r.y + ")");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

