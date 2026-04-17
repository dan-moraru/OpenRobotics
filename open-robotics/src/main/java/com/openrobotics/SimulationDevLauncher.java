package com.openrobotics;

import com.openrobotics.util.ScreenNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

/** Dev launcher that opens the simulation screen directly, bypassing the welcome and setup screens. */
public class SimulationDevLauncher extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("OpenRobotics – Simulation Screen [dev]");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.setWidth(1280);
        primaryStage.setHeight(820);

        ScreenNavigator.setPrimaryStage(primaryStage);
        ScreenNavigator.goToSimulation();
    }

    /**
     * Launches the dev simulation screen.
     *
     * @param args command-line arguments forwarded to {@link javafx.application.Application#launch}.
     */
    public static void main(String[] args) {
        launch(args);
    }
}