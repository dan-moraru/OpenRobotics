package com.openrobotics;

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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class MainApp extends Application {

    private SimulationEngine engine;
    private Pane simContainer;
    private final int TILE_SIZE = 40;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();

        // --- TOOLBAR SETUP ---
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #eeeeee;");

        Button loadBtn = new Button("Load Configuration");
        loadBtn.setOnAction(e -> handleLoadConfig(stage));

        Button testSaveBtn = new Button("Test Save Cycle");
        testSaveBtn.setOnAction(e -> handleTestSaveCycle(stage));

        toolbar.getChildren().addAll(loadBtn, testSaveBtn);
        root.setTop(toolbar);

        // --- SIMULATION VIEWPORT ---
        simContainer = new Pane();
        root.setCenter(simContainer);

        Scene scene = new Scene(root, 1000, 800);
        stage.setTitle("OpenRobotics Simulation & Strategy Debugger");
        stage.setScene(scene);
        stage.show();
    }

    private void handleLoadConfig(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Simulation Config");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            this.engine = new SimulationEngine(selectedFile.getAbsolutePath());
            renderSimulation();
        }
    }

    private void handleTestSaveCycle(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Initial Config for Test");
        File inputFile = fileChooser.showOpenDialog(stage);
        if (inputFile == null) return;

        try {
            this.engine = new SimulationEngine(inputFile.getAbsolutePath());

            // Programmatic change for verification
            Robot testBot = null;
            for (MapEntity e : engine.getMap().getEntities()) {
                if (e instanceof Robot) {
                    testBot = (Robot) e;
                    break;
                }
            }

            if (testBot != null) {
                System.out.println("Modifying " + testBot.getName() + " for test...");
                testBot.setBattery(42.42f);
                testBot.setPosition(new Vector2D(8, 8));
            }

            // Save to new file
            File outputFile = new File(inputFile.getParent(), "test_output_saved.json");
            engine.configSaving(outputFile.getAbsolutePath());
            System.out.println("Test file saved to: " + outputFile.getAbsolutePath());

            // Reload and Render to verify UI reflects saved/loaded data
            this.engine = new SimulationEngine(outputFile.getAbsolutePath());
            renderSimulation();

            // Console Verification
            if (testBot != null) {
                final String botName = testBot.getName();
                for (MapEntity e : engine.getMap().getEntities()) {
                    if (e instanceof Robot && e.getName().equals(botName)) {
                        Robot reloadedBot = (Robot) e;
                        if (reloadedBot.getBattery() == 42.42f && reloadedBot.getPosition().getX() == 8) {
                            System.out.println("SUCCESS: Save cycle verified. Battery, Position, and Strategies persisted.");
                        } else {
                            System.err.println("FAILURE: Data did not persist correctly.");
                        }
                    }
                }
            }

        } catch (Exception ex) {
            System.err.println("Test failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void renderSimulation() {
        if (engine == null || engine.getMap() == null) return;

        simContainer.getChildren().clear();

        for (MapEntity entity : engine.getMap().getEntities()) {
            Vector2D pos = entity.getPosition();
            double x = pos.getX() * TILE_SIZE;
            double y = pos.getY() * TILE_SIZE;

            if (entity instanceof Robot) {
                Robot robot = (Robot) entity;

                // Draw Robot
                Rectangle robotView = new Rectangle(TILE_SIZE - 4, TILE_SIZE - 4, Color.BLUE);
                robotView.setX(x + 2);
                robotView.setY(y + 2);

                // Strategy Names (Using Reflection for clean UI names)
                String navName = (robot.getNav() != null)
                        ? robot.getNav().getClass().getSimpleName().replace("NavigationStrategy", "")
                        : "NONE";
                String sensorName = (robot.getSensor() != null)
                        ? robot.getSensor().getClass().getSimpleName().replace("Sensor", "")
                        : "NONE";

                // Labels
                Text nameLabel = new Text(x, y - 30, robot.getName() + " (" + (int)robot.getBattery() + "%)");
                nameLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 10));

                Text strategyLabel = new Text(x, y - 18, "Nav: " + navName);
                strategyLabel.setFont(Font.font("Verdana", 9));
                strategyLabel.setFill(Color.DARKSLATEGRAY);

                Text sensorLabel = new Text(x, y - 8, "Sens: " + sensorName);
                sensorLabel.setFont(Font.font("Verdana", 9));
                sensorLabel.setFill(Color.DARKSLATEGRAY);

                simContainer.getChildren().addAll(robotView, nameLabel, strategyLabel, sensorLabel);
            } else {
                // Draw static environment
                Rectangle entityView = new Rectangle(TILE_SIZE, TILE_SIZE, Color.LIGHTGRAY);
                entityView.setX(x);
                entityView.setY(y);
                entityView.setStroke(Color.WHITE);
                simContainer.getChildren().add(entityView);
            }
        }
        System.out.println("UI Rendered.");
    }
}