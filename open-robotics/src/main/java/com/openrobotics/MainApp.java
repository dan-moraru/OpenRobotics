package com.openrobotics;

import com.openrobotics.util.ScreenNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Application entry point.
 *
 * <p>Initialises the primary {@link Stage}, registers it with
 * {@link ScreenNavigator}, and navigates to the Welcome screen.
 * All subsequent screen transitions are handled by {@link ScreenNavigator}.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("OpenRobotics \u2013 Warehouse Simulation Platform");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.setWidth(1280);
        primaryStage.setHeight(820);

        ScreenNavigator.setPrimaryStage(primaryStage);
        ScreenNavigator.goToWelcome();
    }

    public static void main(String[] args) {
        launch(args);
    }
}