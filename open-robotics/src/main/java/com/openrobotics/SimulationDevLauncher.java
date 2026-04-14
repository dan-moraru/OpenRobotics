package com.openrobotics;

import com.openrobotics.util.ScreenNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Dev launcher that opens the Simulation screen directly,
 * bypassing Welcome and Setup. Run this class from the IDE
 * while the screens are not yet fully stitched together.
 */
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

    public static void main(String[] args) {
        launch(args);
    }
}