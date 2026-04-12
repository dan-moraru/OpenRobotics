package com.openrobotics;

import com.openrobotics.db.Database;
import com.openrobotics.util.ScreenNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Application entry point.
 *
 * <p>Initialises the primary {@link Stage}, registers it with
 * {@link ScreenNavigator}, and navigates to the Welcome screen.
 * All subsequent screen transitions are handled by {@link ScreenNavigator}.
 */
public class MainApp extends Application {

    @Override
    public void init() {
        // Database initialization
        try {
            Database.init();
            System.out.println("Database initialized successfully.");
        } catch (Exception e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("OpenRobotics \u2013 Warehouse Simulation Platform");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.setWidth(1920);
        primaryStage.setHeight(1080);

        ScreenNavigator.setPrimaryStage(primaryStage);
        ScreenNavigator.goToWelcome();
    }

    public static void main(String[] args) {
        launch(args);
    }
}