package com.openrobotics;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        // Can create different types of scaffolding (hbox, vbox, etc)
        Pane root = new Pane();

        // robot now extends mapentity — constructor takes name and vector2d position
        Robot myRobot = new Robot("BOT-001", new Vector2D(100, 150));

        // Rectangle represents a robot
        Rectangle robotView = new Rectangle(50, 50, Color.BLUE);
        robotView.setX(myRobot.getPosition().getX());
        robotView.setY(myRobot.getPosition().getY());

        // Give IDs or classes/labels (not sure) for organization and instrumental testing
        robotView.setId("robotShape");

        Text label = new Text(myRobot.getPosition().getX(), myRobot.getPosition().getY() - 10,
                "ID: " + myRobot.getName());

        // Must always assign children objects to their parent/root groups/objects!
        root.getChildren().addAll(robotView, label);

        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Warehouse Simulation Test");
        stage.setScene(scene);
        stage.show();
    }
}
