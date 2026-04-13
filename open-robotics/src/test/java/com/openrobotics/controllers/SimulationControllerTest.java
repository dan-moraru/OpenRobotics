package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.util.ScreenNavigator;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationControllerTest extends ApplicationTest {

    /**
     * Robot clicks often miss small controls in headless/CI; firing runs the same {@code onAction} as the FXML button.
     */
    private void clearConsoleThroughUi() {
        Button clear = lookup("#clearConsoleButton").queryAs(Button.class);
        interact(clear::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void assertConsoleEventuallyEmpty() throws TimeoutException {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () ->
                lookup("#consoleArea").queryAs(TextArea.class).getText().isEmpty());
    }

    private SimulationEngine buildEngine(CoordinationPolicy policy) {
        return new SimulationEngine(new Map(6, 6), new Robot[]{}, new Dispatcher(), policy);
    }

    private void reloadSimulationWithEngine(SimulationEngine engine) {
        interact(() -> {
            AppState.clear();
            AppState.setCanvasDimensions(30, 30);
            AppState.setEngine(engine);
            ScreenNavigator.goToSimulation();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

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
    void resetState() throws TimeoutException {
        clickOn("↺"); // restart button → resets running/paused flags
        WaitForAsyncUtils.waitForFxEvents();
        clearConsoleThroughUi();
        assertConsoleEventuallyEmpty();
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
    void clear_console_empties_output() throws TimeoutException {
        clickOn("▶");  // produce some console output
        WaitForAsyncUtils.waitForFxEvents();
        TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
        assertFalse(console.getText().isEmpty(), "Console should have content after play");
        clearConsoleThroughUi();
        assertConsoleEventuallyEmpty();
        assertEquals("", lookup("#consoleArea").queryAs(TextArea.class).getText());
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

    @Test
    void intersection_tile_visible_for_traffic_rules_engine() {
        reloadSimulationWithEngine(buildEngine(new TrafficRulesPolicy(new java.util.HashSet<>())));

        Button intersectionButton = lookup("#intersectionObjectTile").queryAs(Button.class);
        assertTrue(intersectionButton.isVisible());
        assertTrue(intersectionButton.isManaged());
    }

    @Test
    void intersection_tile_hidden_for_reservation_policy_engine() {
        reloadSimulationWithEngine(buildEngine(new ReservationKPolicy(3)));

        Button intersectionButton = lookup("#intersectionObjectTile").queryAs(Button.class);
        assertFalse(intersectionButton.isVisible());
        assertFalse(intersectionButton.isManaged());
    }

    @Test
    void intersection_tile_hidden_for_no_op_engine() {
        reloadSimulationWithEngine(buildEngine(CoordinationPolicy.noOp()));

        Button intersectionButton = lookup("#intersectionObjectTile").queryAs(Button.class);
        assertFalse(intersectionButton.isVisible());
        assertFalse(intersectionButton.isManaged());
    }
}
