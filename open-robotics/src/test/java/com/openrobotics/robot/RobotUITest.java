package com.openrobotics.robot;

import java.io.IOException;
import javafx.stage.Stage;
import org.testfx.framework.junit5.ApplicationTest;

import com.openrobotics.MainApp;

public class RobotUITest extends ApplicationTest {

    /**
     * Start the main application and set the stage.
     */
    @Override
    public void start(Stage stage) {
        try {
            new MainApp().start(stage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
