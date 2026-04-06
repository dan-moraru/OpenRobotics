package com.openrobotics.robot;

import javafx.stage.Stage;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import org.testfx.framework.junit5.ApplicationTest;

import com.openrobotics.MainApp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RobotUITest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        new MainApp().start(stage);
    }

    // @Test
    // public void should_verify_robot_color() {
    //     // Find the rectangle by the ID we set in MainApp
    //     Rectangle robot = lookup("#robotShape").queryAs(Rectangle.class);

    //     // Assert that the fill color is BLUE
    //     assertEquals(Color.BLUE, robot.getFill(), "The robot should be blue!");
    // }
}
