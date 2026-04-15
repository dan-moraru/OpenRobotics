package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JavaFX integration-style tests for the Results screen/controller flow.
 *
 * <p>This suite seeds {@link AppState} with a small deterministic simulation, opens
 * {@code ResultsScreen.fxml}, and verifies that table/stat labels, charts, and navigation actions
 * reflect the seeded engine state.</p>
 */
public class ResultsControllerTest extends ApplicationTest {

    /**
     * Seeds global {@link AppState} with a minimal but complete simulation model used by all tests.
     *
     * <p>The seeded state includes:
     * <ul>
     *   <li>an 8x8 map,</li>
     *   <li>one robot with a concrete navigation strategy and sensor,</li>
     *   <li>one queued task in the dispatcher, and</li>
     *   <li>a known config path for run-name assertions.</li>
     * </ul>
     */
    private void seedAppState() {
        Map map = new Map(8, 8);
        Robot robot = new Robot("R1", new Vector2D(1, 1));
        robot.setNav(new GreedyNavigationStrategy(42L));
        robot.setSensor(new ProximitySensor());
        map.addEntity(robot);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(new Task(1, new Vector2D(1, 1), new Vector2D(2, 2), 1));

        SimulationEngine engine = new SimulationEngine(map, new Robot[]{ robot }, dispatcher, CoordinationPolicy.noOp());
        AppState.clear();
        AppState.setEngine(engine);
        AppState.setConfigPath("results-test.json");
    }

    /**
     * Loads the results screen onto the JavaFX stage after seeding application state.
     *
     * @param stage the stage provided by TestFX
     * @throws Exception if FXML loading fails
     */
    @Override
    public void start(Stage stage) throws Exception {
        seedAppState();
        ScreenNavigator.setPrimaryStage(stage);
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/openrobotics/fxml/ResultsScreen.fxml"));
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1200, 700));
        stage.show();
    }

    /**
     * Verifies that initial table and summary labels are populated from the seeded engine.
     *
     * <p>Confirms robot stats row count, tick label, run-name source, and top-level task metrics
     * shown in both summary and table info labels.</p>
     */
    @Test
    void results_screen_populates_table_and_summary_from_engine() {
        WaitForAsyncUtils.waitForFxEvents();

        TableView<?> table = lookup("#robotStatsTable").queryAs(TableView.class);
        assertEquals(1, table.getItems().size());
        assertEquals("Ticks: 0", lookup("#simTicksLabel").queryAs(Label.class).getText());
        assertTrue(lookup("#runNameLabel").queryAs(Label.class).getText().contains("results-test.json"));
        assertTrue(lookup("#statTotalTasks").queryAs(Label.class).getText().contains("1"));
        assertTrue(lookup("#tableInfoLabel").queryAs(Label.class).getText().contains("1"));
    }

    /**
     * Verifies that both chart containers are populated when at least one robot exists.
     *
     * <p>Asserts non-empty chart containers and validates that the rendered chart nodes are
     * {@link BarChart} instances.</p>
     */
    @Test
    void results_screen_creates_charts_when_robots_exist() {
        WaitForAsyncUtils.waitForFxEvents();

        StackPane chart1 = lookup("#chartContainer1").queryAs(StackPane.class);
        StackPane chart2 = lookup("#chartContainer2").queryAs(StackPane.class);

        assertFalse(chart1.getChildren().isEmpty());
        assertFalse(chart2.getChildren().isEmpty());
        assertTrue(chart1.getChildren().get(0) instanceof BarChart);
        assertTrue(chart2.getChildren().get(0) instanceof BarChart);
    }

    /**
     * Verifies that clicking {@code NEW SIMULATION} navigates back to setup.
     *
     * <p>Navigation success is asserted using presence of a setup-screen control
     * ({@code #mapCombo}).</p>
     */
    @Test
    void new_simulation_button_navigates_to_setup_screen() {
        clickOn("NEW SIMULATION");
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lookup("#mapCombo").tryQuery().isPresent());
    }

    /**
     * Verifies that clicking {@code RETURN TO EDITOR} navigates to the simulation/editor screen.
     *
     * <p>Navigation success is asserted by checking the editor tick label content.</p>
     */
    @Test
    void return_to_editor_button_navigates_to_simulation_screen() {
        clickOn("RETURN TO EDITOR");
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("TICK 0", lookup("#tickDisplayLabel").queryAs(Label.class).getText());
    }
}
