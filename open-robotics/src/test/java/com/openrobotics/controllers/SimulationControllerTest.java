package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationControllerTest extends ApplicationTest {

    @Override
    public void start(Stage stage) throws Exception {
        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
        ScreenNavigator.setPrimaryStage(stage);
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/openrobotics/fxml/SimulationScreen.fxml"));
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1200, 700));
        stage.show();
    }

    /** Reset to a clean slate before every test: restart simulation, then clear the console. */
    @BeforeEach
    void resetState() {
        clickOn("↺"); // restart button → resets running/paused flags
        clickOn("✕");  // clear console
    }

    // @Test
    // void play_logs_started_message() {
    //     clickOn("▶");
    //     WaitForAsyncUtils.waitForFxEvents();
    //     TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
    //     assertTrue(console.getText().contains("Simulation started."));
    // }

    // @Test
    // void pause_after_play_logs_paused_message() {
    //     clickOn("▶");
    //     clickOn("⏸");
    //     WaitForAsyncUtils.waitForFxEvents();
    //     TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
    //     assertTrue(console.getText().contains("Simulation paused."));
    // }

    @Test
    void restart_logs_reset_message() {
        clickOn("▶");  // start first so restart has something to stop
        clickOn("↺");
        WaitForAsyncUtils.waitForFxEvents();
        TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
        assertTrue(console.getText().contains("Simulation reset."));
    }

    @Test
    void clear_console_empties_output() {
        clickOn("▶");  // produce some console output
        TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
        assertFalse(console.getText().isEmpty(), "Console should have content after play");
        clickOn("✕");
        assertEquals("", console.getText());
    }

    @Test
    void play_without_loaded_engine_logs_warning() {
        clickOn("▶");
        WaitForAsyncUtils.waitForFxEvents();

        TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
        assertTrue(console.getText().contains("No simulation loaded. Return to Setup and load a config."));
    }

    @Test
    void step_without_loaded_engine_logs_warning() {
        clickOn("▶▶");
        WaitForAsyncUtils.waitForFxEvents();

        TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
        assertTrue(console.getText().contains("No simulation loaded."));
    }

    @Test
    void results_tab_navigates_to_results_screen() {
        clickOn("RESULTS");
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(lookup("#robotStatsTable").queryAs(TableView.class));
    }

    @Test
    void tick_label_shows_TICK_0_after_restart() {
        Label tickLabel = lookup("#tickDisplayLabel").queryAs(Label.class);
        assertEquals("TICK 0", tickLabel.getText());
    }
}
