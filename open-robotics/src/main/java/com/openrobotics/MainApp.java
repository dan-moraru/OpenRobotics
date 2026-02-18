package com.openrobotics;

import com.openrobotics.db.Database;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class MainApp extends Application {

    public static void main(String[] args) {
        try {
            Database.init();
        } catch (IOException | SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // Can create different types of scaffolding (hbox, vbox, etc)
        Pane root = new Pane();

        Robot myRobot = new Robot("BOT-001", 100, 150);

        // Rectangle represents a robot
        Rectangle robotView = new Rectangle(50, 50, Color.BLUE);
        robotView.setX(myRobot.getX());
        robotView.setY(myRobot.getY());

        // Give IDs or classes/labels (not sure) for organization and instrumental testing
        robotView.setId("robotShape");

        Text label = new Text(myRobot.getX(), myRobot.getY() - 10, "ID: " + myRobot.getId());

        // Must always assign children objects to their parent/root groups/objects!
        root.getChildren().addAll(robotView, label);

        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Warehouse Simulation Test");
        stage.setScene(scene);
        stage.show();
    }
}