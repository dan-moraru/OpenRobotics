package com.openrobotics;

import java.io.IOException;

import com.openrobotics.util.ScreenNavigator;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JavaFX startup test for {@link MainApp}.
 *
 * <p>This suite verifies application bootstrap wiring for the primary stage, including title,
 * minimum/initial dimensions, and initial welcome-screen loading.</p>
 */
public class MainAppTest extends ApplicationTest {

    /** Primary stage provided by TestFX and initialized by {@link MainApp#start(Stage)}. */
    private Stage primaryStage;

    /**
     * Launches the real application entrypoint on the TestFX stage.
     *
     * @param stage JavaFX stage supplied by TestFX
     */
    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        try {
            new MainApp().start(stage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies application start configures stage metadata and loads the welcome root node.
     */
    @Test
    void start_configures_primary_stage_and_loads_welcome_screen() {
        WaitForAsyncUtils.waitForFxEvents();

        assertSame(primaryStage, ScreenNavigator.getPrimaryStage());
        assertEquals("OpenRobotics – Warehouse Simulation Platform", primaryStage.getTitle());
        assertEquals(960.0, primaryStage.getMinWidth());
        assertEquals(640.0, primaryStage.getMinHeight());
        assertEquals(1920.0, primaryStage.getWidth());
        assertEquals(1080.0, primaryStage.getHeight());
        assertTrue(lookup("#rootPane").tryQuery().isPresent());
    }
}
