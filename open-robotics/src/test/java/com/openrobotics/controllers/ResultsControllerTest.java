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

public class ResultsControllerTest extends ApplicationTest {

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

    @Test
    void new_simulation_button_navigates_to_setup_screen() {
        clickOn("NEW SIMULATION");
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lookup("#mapCombo").tryQuery().isPresent());
    }

    @Test
    void return_to_editor_button_navigates_to_simulation_screen() {
        clickOn("RETURN TO EDITOR");
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("TICK 0", lookup("#tickDisplayLabel").queryAs(Label.class).getText());
    }
}
