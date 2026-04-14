package com.openrobotics;

import com.openrobotics.util.ScreenNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

/** dev launcher that opens the simulation screen directly, bypassing welcome and setup */
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