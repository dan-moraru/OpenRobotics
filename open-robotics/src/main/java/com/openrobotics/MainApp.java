package com.openrobotics;

import com.openrobotics.controllers.SimulationController;
import com.openrobotics.db.Database;
import com.openrobotics.util.ScreenNavigator;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

/** Application entry point; initialises the primary stage, connects to the database, and navigates to the welcome screen. */
public class MainApp extends Application {
    private SimulationController controller; 

    @Override
    public void init() {
        try {
            Database.init();
            System.out.println("Database initialized successfully.");
        } catch (Exception e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        primaryStage.setTitle("OpenRobotics \u2013 Warehouse Simulation Platform");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.setWidth(1920);
        primaryStage.setHeight(1080);

        ScreenNavigator.setPrimaryStage(primaryStage);
        ScreenNavigator.goToWelcome();
    }

    @Override
    public void stop() {
        try {
            Database.shutdown();
            System.out.println("Database connection closed successfully.");
        } catch (Exception e) {
            System.err.println("Database shutdown failure: " + e.getMessage());
        }

        // shut down the simulation controller if it is the active screen
        Object controller = ScreenNavigator.getCurrentController();

        if (controller instanceof SimulationController simController) {
            simController.shutdown();
        }

        System.out.println("Shutdown complete.");
    }

    /**
     * Launches the JavaFX application.
     *
     * @param args command-line arguments forwarded to {@link javafx.application.Application#launch}.
     */
    public static void main(String[] args) {
        launch(args);
    }
}