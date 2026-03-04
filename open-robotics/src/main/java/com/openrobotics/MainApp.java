package com.openrobotics;

import com.openrobotics.common.Database;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.SimulationEngine;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class MainApp extends Application {

    private SimulationEngine engine;
    private Pane simContainer; // The area where robots are drawn
    private final int TILE_SIZE = 40; // Scale: 1 tile = 40 pixels

    public static void main(String[] args) {
//        try {
//            Database.init();
//        } catch (IOException | SQLException e) {
//            e.printStackTrace();
//            System.exit(1);
//        }
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();

        // 1. Setup Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #eeeeee;");

        Button loadBtn = new Button("Load Configuration");
        loadBtn.setOnAction(e -> handleLoadConfig(stage));
        Button testSaveBtn = new Button("Test Save Cycle");
        testSaveBtn.setOnAction(e -> handleTestSaveCycle(stage));

        toolbar.getChildren().addAll(loadBtn, testSaveBtn);
        root.setTop(toolbar);

        // 2. Setup Simulation Container
        simContainer = new Pane();
        root.setCenter(simContainer);

        Scene scene = new Scene(root, 1000, 800);
        stage.setTitle("OpenRobotics Simulation Test");
        stage.setScene(scene);
        stage.show();
    }

    private void handleLoadConfig(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Simulation Config");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            // Initialize the engine with the file
            this.engine = new SimulationEngine(selectedFile.getAbsolutePath());

            // Refresh the UI to show the loaded data
            renderSimulation();
        }
    }

    private void handleTestSaveCycle(Stage stage) {
        // 1. Load an initial file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Initial Config for Test");
        File inputFile = fileChooser.showOpenDialog(stage);
        if (inputFile == null) return;

        try {
            // Initialize engine
            this.engine = new SimulationEngine(inputFile.getAbsolutePath());

            // 2. Make a programmatic change
            // Let's find the first robot and change its battery and move it
            Robot testBot = null;
            for (MapEntity e : engine.getMap().getEntities()) {
                if (e instanceof Robot) {
                    testBot = (Robot) e;
                    break;
                }
            }

            if (testBot != null) {
                System.out.println("Modifying " + testBot.getName() + " for test...");
                testBot.setBattery(42.42f); // Specific value to look for later
                testBot.setPosition(new Vector2D(8, 8)); // Move to a specific spot
            }

            // 3. Save to a NEW file
            File outputFile = new File(inputFile.getParent(), "test_output_saved.json");
            engine.configSaving(outputFile.getAbsolutePath());
            System.out.println("Test file saved to: " + outputFile.getAbsolutePath());

            // 4. Verification: Peek into the saved file to see if changes stuck
            // We can reload the engine from the NEW file to see if the UI updates
            this.engine = new SimulationEngine(outputFile.getAbsolutePath());
            renderSimulation();

            // Final Console Verification
            for (MapEntity e : engine.getMap().getEntities()) {
                if (e instanceof Robot && e.getName().equals(testBot.getName())) {
                    Robot reloadedBot = (Robot) e;
                    if (reloadedBot.getBattery() == 42.42f && reloadedBot.getPosition().getX() == 8) {
                        System.out.println("SUCCESS: Save cycle verified. Battery and Position persisted.");
                    } else {
                        System.err.println("FAILURE: Data did not persist correctly.");
                    }
                }
            }

        } catch (Exception ex) {
            System.err.println("Test failed with error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void renderSimulation() {
        if (engine == null || engine.getMap() == null) return;

        // Clear previous view
        simContainer.getChildren().clear();

        // 1. Draw the Grid/Map Entities
        for (MapEntity entity : engine.getMap().getEntities()) {
            Vector2D pos = entity.getPosition();

            // Calculate pixel coordinates based on TILE_SIZE
            double x = pos.getX() * TILE_SIZE;
            double y = pos.getY() * TILE_SIZE;

            if (entity instanceof Robot) {
                // Draw Robot as Blue Square
                Rectangle robotView = new Rectangle(TILE_SIZE - 4, TILE_SIZE - 4, Color.BLUE);
                robotView.setX(x + 2);
                robotView.setY(y + 2);

                Text label = new Text(x, y - 5, "Robot: " + entity.getName());
                simContainer.getChildren().addAll(robotView, label);
            } else {
                // Draw other entities (Stations, Obstacles) as Grey Squares
                Rectangle entityView = new Rectangle(TILE_SIZE, TILE_SIZE, Color.GRAY);
                entityView.setX(x);
                entityView.setY(y);
                simContainer.getChildren().add(entityView);
            }
        }

        System.out.println("UI Rendered with " + engine.getMap().getEntities().size() + " entities.");
    }
}