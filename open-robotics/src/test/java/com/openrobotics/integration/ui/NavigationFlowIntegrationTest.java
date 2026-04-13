package com.openrobotics.integration.ui;

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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NavigationFlowIntegrationTest extends ApplicationTest {

    @Override
    public void start(Stage stage) throws Exception {
        seedAppState();
        ScreenNavigator.setPrimaryStage(stage);
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/openrobotics/fxml/WelcomeScreen.fxml"));
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1200, 700));
        stage.show();
    }

    @Test
    void welcomeToSetupToSimulationFlowWorks() throws TimeoutException {
        fireButton("#welcomeStartSetupButton");

        ComboBox<?> mapCombo = lookup("#mapCombo").queryAs(ComboBox.class);
        assertEquals("empty", mapCombo.getValue());

        fireButton("#setupNextButton");
        waitForSimulationScreen();

        TextArea console = lookup("#consoleArea").queryAs(TextArea.class);
        assertTrue(console.getText().contains("Loaded simulation with"));
        assertEquals("TICK 0", lookup("#tickDisplayLabel").queryAs(Label.class).getText());
    }

    @Test
    void simulationResultsAndBackToEditorFlowWorks() throws TimeoutException {
        fireButton("#welcomeStartSetupButton");
        fireButton("#setupNextButton");
        waitForSimulationScreen();

        fireButton("#resultsTabBtn");
        waitForResultsScreen();

        assertEquals(1, lookup("#robotStatsTable").queryAs(TableView.class).getItems().size());
        assertTrue(lookup("#statTotalTasks").queryAs(Label.class).getText().contains("1"));

        fireButton("#returnToEditorButton");
        WaitForAsyncUtils.waitForFxEvents();
        waitForSimulationScreen();

        assertEquals("TICK 0", lookup("#tickDisplayLabel").queryAs(Label.class).getText());
    }

    private void seedAppState() {
        Map map = new Map(5, 3);
        Robot robot = new Robot("UiBot", new Vector2D(0, 0));
        robot.setNav(new GreedyNavigationStrategy(19L));
        robot.setSensor(new ProximitySensor());
        map.addEntity(robot);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(new Task(1, new Vector2D(1, 0), new Vector2D(2, 0), 1));

        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
        AppState.setEngine(new SimulationEngine(
                map,
                new Robot[]{robot},
                dispatcher,
                CoordinationPolicy.noOp()
        ));
        AppState.setConfigPath("ui-integration.json");
    }

    /** Fires the action without relying on synthetic mouse events (more reliable on CI / headless). */
    private void fireButton(String query) {
        interact(() -> lookup(query).queryAs(Button.class).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void waitForSimulationScreen() throws TimeoutException {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> lookup("#consoleArea").tryQuery().isPresent());
    }

    private void waitForResultsScreen() throws TimeoutException {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> lookup("#robotStatsTable").tryQuery().isPresent());
    }
}
