package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.util.ScreenNavigator;
import javafx.event.Event;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JavaFX integration-style tests for {@link SimulationController}.
 *
 * <p>This suite exercises simulation-screen behavior across loading, editing, playback controls,
 * outliner/property synchronization, configuration persistence/reload, screen navigation, and
 * policy-dependent UI visibility. Tests intentionally interact with real FXML scene wiring while
 * using reflection helpers for private controller paths that are difficult to trigger only through
 * public UI events.</p>
 */
public class SimulationControllerTest extends ApplicationTest {

    @TempDir
    Path tempDir;

    private Stage stage;
    private SimulationController controller;

    /**
     * Creates a minimal engine using the supplied coordination policy.
     */
    private SimulationEngine buildEngine(CoordinationPolicy policy) {
        return new SimulationEngine(new Map(6, 6), new Robot[]{}, new Dispatcher(), policy);
    }

    /**
     * Reloads simulation screen from a specific engine through {@link AppState}.
     */
    private void reloadSimulationWithEngine(SimulationEngine engine) {
        interact(() -> {
            AppState.clear();
            AppState.setCanvasDimensions(30, 30);
            AppState.setEngine(engine);
            ScreenNavigator.goToSimulation();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Initializes the simulation scene on the TestFX stage.
     *
     * @param stage JavaFX stage from TestFX
     * @throws Exception if FXML loading fails
     */
    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        Logger.setMode(LoggerMode.NO_OP);
        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
        ScreenNavigator.setPrimaryStage(stage);
        loadSceneFromCurrentAppState();
    }

    /**
     * Resets UI/controller runtime state before each test.
     *
     * @throws TimeoutException if console clear does not complete in time
     */
    @BeforeEach
    void resetState() throws TimeoutException {
        Logger.setMode(LoggerMode.NO_OP);
        fireButtonByText("↺");
        clearConsoleThroughUi();
        assertConsoleEventuallyEmpty();
    }

    /**
     * Cleans controller resources and shared app/logging state after each test.
     */
    @AfterEach
    void cleanUpController() {
        if (controller != null) {
            interact(controller::cleanup);
            WaitForAsyncUtils.waitForFxEvents();
        }
        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
        Logger.setMode(LoggerMode.DB);
    }

    /** Verifies safe empty-state labels/progress when no engine is loaded. */
    @Test
    void initial_screen_without_engine_shows_safe_empty_state() {
        assertEquals("TICK 0", tickLabel().getText());
        assertEquals(0.0, progressBar().getProgress(), 0.001);
        assertEquals("Objects: 0", field("objsLabel", Label.class).getText());
        assertTrue(field("ramLabel", Label.class).getText().startsWith("RAM: "));
        assertEquals("Canvas Size: 600×600 tiles", field("canvasSizeLabel", Label.class).getText());
        assertTrue(field("viewportStatusLabel", Label.class).getText().startsWith("Select"));
        assertEquals("Edit mode", field("editModeLabel", Label.class).getText());
        assertTrue(field("tipLabel", Label.class).getText().startsWith("TIP: "));
        assertTrue(consoleText().isEmpty(), "BeforeEach should leave console empty for each test");
    }

    /** Verifies malformed config reload reports failure without crashing the screen. */
    @Test
    void loading_invalid_config_path_reports_failure_without_crashing() throws Exception {
        Path malformed = tempDir.resolve("bad-config.json");
        Files.writeString(malformed, "{ not valid json");

        loadScreenWith(null, malformed.toString(), 30, 30);

        assertEquals("Load failed", field("viewportStatusLabel", Label.class).getText());
        assertTrue(consoleText().contains("Config reload failed"));
        assertNull(AppState.getEngine());
    }

    /** Verifies loading a populated engine refreshes canvas, outliner, stats, and console logs. */
    @Test
    void loading_engine_populates_canvas_outliner_stats_and_console() {
        EngineFixture fixture = loadDiverseEngine();

        ListView<String> outliner = outliner();

        assertAll(
                () -> assertSame(fixture.engine, AppState.getEngine()),
                () -> assertEquals("Canvas Size: 6×5 Tiles", field("canvasSizeLabel", Label.class).getText()),
                () -> assertEquals("Loaded 5 objects", field("viewportStatusLabel", Label.class).getText()),
                () -> assertEquals("Objects: 6", field("objsLabel", Label.class).getText()),
                () -> assertEquals(6, outliner.getItems().size()),
                () -> assertTrue(outliner.getItems().stream().anyMatch(item -> item.contains("robot_1"))),
                () -> assertTrue(outliner.getItems().stream().anyMatch(item -> item.contains("rack_1"))),
                () -> assertTrue(outliner.getItems().stream().anyMatch(item -> item.contains("Task #99"))),
                () -> assertTrue(consoleText().contains("Loaded simulation with 5 entities")),
                () -> assertTrue(consoleText().contains("Simulation screen ready"))
        );
    }

    /** Verifies outliner filtering updates visible rows and derived object count label. */
    @Test
    void outliner_filter_updates_entities_tasks_and_object_count() {
        loadDiverseEngine();
        TextField search = field("outlinerSearchField", TextField.class);

        interact(() -> search.setText("task #99"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(List.of("⬇ Task #99 PENDING ((1, 0) → (3, 0))"), outliner().getItems());
        assertEquals("Objects: 1", field("objsLabel", Label.class).getText());

        interact(() -> search.setText("no-match"));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(outliner().getItems().isEmpty());
        assertEquals("Objects: 0", field("objsLabel", Label.class).getText());

        interact(search::clear);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(6, outliner().getItems().size());
        assertEquals("Objects: 6", field("objsLabel", Label.class).getText());
    }

    /** Verifies selecting an outliner entity populates properties panel with entity details. */
    @Test
    void selecting_outliner_entity_populates_properties_panel() {
        EngineFixture fixture = loadDiverseEngine();

        interact(() -> {
            outliner().getSelectionModel().select(0);
            invokePrivate("onOutlinerSelect", new Class<?>[0]);
        });
        WaitForAsyncUtils.waitForFxEvents();

        String panelText = propertiesPanelText();
        assertTrue(panelText.contains(fixture.robot.getName()));
        assertTrue(panelText.contains("Type:"));
        assertTrue(panelText.contains("Robot"));
        assertTrue(panelText.contains("Battery:"));
    }

    /** Verifies renaming through properties panel updates model and outliner text. */
    @Test
    void property_name_edit_updates_entity_outliner_and_viewport() {
        EngineFixture fixture = loadDiverseEngine();
        invokeOnFx("selectEntity", new Class<?>[]{MapEntity.class}, fixture.rack);

        TextField nameField = textFieldWithValue("rack_1");
        interact(nameField::requestFocus);
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> nameField.setText("renamed_rack"));
        interact(() -> nameField.getParent().requestFocus());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("renamed_rack", fixture.rack.getName());
        assertTrue(outliner().getItems().stream().anyMatch(item -> item.contains("renamed_rack")));
    }

    /** Verifies entity factory type mapping and placement rules for common tile occupancy cases. */
    @Test
    void factory_creates_expected_entity_types_and_occupancy_rules() {
        loadDiverseEngine();

        MapEntity robot = createdEntity("robot", 4, 4);
        MapEntity charger = createdEntity("CHARGING", 4, 4);
        MapEntity station = createdEntity("delivery", 4, 4);
        MapEntity rack = createdEntity("SHELF", 4, 4);
        MapEntity wall = createdEntity("OBSTACLE", 4, 4);
        MapEntity fallback = createdEntity("MYSTERY", 4, 4);

        assertAll(
                () -> assertInstanceOf(Robot.class, robot),
                () -> assertNotNull(((Robot) robot).getNav()),
                () -> assertNotNull(((Robot) robot).getSensor()),
                () -> assertInstanceOf(ChargingStation.class, charger),
                () -> assertInstanceOf(DeliveryStation.class, station),
                () -> assertInstanceOf(Rack.class, rack),
                () -> assertInstanceOf(Obstacle.class, wall),
                () -> assertEquals(MapEntity.class, fallback.getClass()),
                () -> assertFalse(canPlace(createdEntity("ROBOT", 0, 0), 0, 0, null)),
                () -> assertTrue(canPlace(createdEntity("CHARGER", 0, 0), 0, 0, null)),
                () -> assertTrue(canPlace(createdEntity("ROBOT", 0, 1), 0, 1, null)),
                () -> assertFalse(canPlace(createdEntity("DOCK", 0, 1), 0, 1, null)),
                () -> assertFalse(canPlace(createdEntity("RACK", 0, 1), 0, 1, null)),
                () -> assertTrue(canPlace(createdEntity("RACK", 4, 4), 4, 4, null))
        );
    }

    /** Verifies copy/paste/delete operations mutate engine entities and emit console feedback. */
    @Test
    void copy_paste_and_delete_selected_entity_update_engine_and_console() {
        EngineFixture fixture = loadDiverseEngine();
        int initialCount = fixture.engine.getMap().getEntities().size();

        invokeOnFx("selectEntity", new Class<?>[]{MapEntity.class}, fixture.rack);
        invokeOnFx("copySelected", new Class<?>[0]);
        invokeOnFx("pasteClipboard", new Class<?>[0]);

        assertEquals(initialCount + 1, fixture.engine.getMap().getEntities().size());
        assertTrue(consoleText().contains("Copied rack_1."));
        assertTrue(consoleText().contains("Pasted rack_1_copy"));
        assertTrue(outliner().getItems().stream().anyMatch(item -> item.contains("rack_1_copy")));

        invokeOnFx("deleteSelected", new Class<?>[0]);

        assertEquals(initialCount, fixture.engine.getMap().getEntities().size());
        assertTrue(consoleText().contains("Deleted rack_1_copy."));

        invokeOnFx("deleteSelected", new Class<?>[0]);

        assertEquals(initialCount, fixture.engine.getMap().getEntities().size());
    }

    /** Verifies task pickup/dropoff coordinates are synchronized when referenced entities move. */
    @Test
    void sync_task_positions_updates_pickup_and_dropoff_references() {
        EngineFixture fixture = loadDiverseEngine();

        invokeOnFx(
                "syncTaskPositions",
                new Class<?>[]{Vector2D.class, Vector2D.class},
                new Vector2D(1, 0),
                new Vector2D(2, 1)
        );
        invokeOnFx(
                "syncTaskPositions",
                new Class<?>[]{Vector2D.class, Vector2D.class},
                new Vector2D(3, 0),
                new Vector2D(4, 1)
        );

        assertEquals(new Vector2D(2, 1), fixture.task.getPickupLocation());
        assertEquals(new Vector2D(4, 1), fixture.task.getDropoffLocation());
        assertTrue(consoleText().contains("Updated 1 task(s) to reflect entity move"));
        assertTrue(outliner().getItems().stream().anyMatch(item -> item.contains("(2, 1) → (4, 1)")));
    }

    /** Verifies single-step frame advances simulation tick/progress/status and logs. */
    @Test
    void next_frame_with_engine_advances_tick_status_progress_and_console() {
        loadScreenWith(emptyEngine(), null, 30, 30);
        Label simStatus = installOptionalStatusLabel();

        invokeOnFx("onNextFrame", new Class<?>[0]);

        assertEquals("STEPPING", simStatus.getText());
        assertEquals("TICK 1", tickLabel().getText());
        assertEquals(0.001, progressBar().getProgress(), 0.0001);
        assertTrue(consoleText().contains("Step → TICK 1"));
    }

    /** Verifies completion path updates status/progress without incrementing tick counter. */
    @Test
    void completing_tick_updates_complete_state_without_incrementing_tick() {
        loadScreenWith(new CompletingEngine(), null, 30, 30);
        Label simStatus = installOptionalStatusLabel();

        invokeOnFx("onNextFrame", new Class<?>[0]);

        assertEquals("COMPLETE", simStatus.getText());
        assertEquals("Workload complete", field("viewportStatusLabel", Label.class).getText());
        assertEquals("TICK 0", tickLabel().getText());
        assertEquals(0.0, progressBar().getProgress(), 0.001);
        assertTrue(consoleText().contains("Simulation complete at TICK 0."));
        assertTrue(consoleText().contains("Step → TICK 0"));
    }

    /** Verifies play/pause/replay/stop flow updates status, button styles, and timeline logs. */
    @Test
    void play_pause_resume_and_stop_flow_updates_labels_buttons_and_logs() {
        loadScreenWith(emptyEngine(), null, 30, 30);
        Label simStatus = installOptionalStatusLabel();

        fireButton("playBtn");
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("FAILURE", simStatus.getText());
        assertTrue(consoleText().contains("\u26a0 No tasks could be generated."));
        assertTrue(consoleText().contains("Simulation failed: The map configuration is invalid. Simulation cannot run."));
        assertTrue(consoleText().contains("Simulation failed at TICK 0."));
        assertNotNull(field("initialSnapshotPath", String.class));
        assertEquals("", field("playBtn", Button.class).getStyle());
        assertEquals("", field("pauseBtn", Button.class).getStyle());

        invokeOnFx("onStop", new Class<?>[0]);

        assertEquals("STOPPED", simStatus.getText());
        assertTrue(consoleText().contains("Simulation stopped."));
        assertEquals("", field("playBtn", Button.class).getStyle());
        assertEquals("", field("pauseBtn", Button.class).getStyle());
    }

    /** Verifies play still starts when baseline snapshot save fails, while logging failure context. */
    @Test
    void play_logs_snapshot_failure_but_still_starts_when_engine_cannot_save_baseline() {
        loadScreenWith(new UnsavableEngine(), null, 30, 30);
        Label simStatus = installOptionalStatusLabel();

        fireButton("playBtn");
        WaitForAsyncUtils.waitForFxEvents();
        invokeOnFx("onStop", new Class<?>[0]);

        assertEquals("STOPPED", simStatus.getText());
        assertTrue(consoleText().contains("\u26a0 Could not snapshot initial state: snapshot failed"));
        assertTrue(consoleText().contains("\u26a0 No tasks could be generated."));
        assertTrue(consoleText().contains("Simulation failed: The map configuration is invalid. Simulation cannot run."));
        assertFalse(consoleText().contains("Simulation started."));
    }

    /** Verifies speed controls update internal speed factor and append matching log entries. */
    @Test
    void speed_buttons_update_speed_factor_and_log_each_choice() {
        fireButton("speed2Btn");
        assertEquals(2.0, field("speedFactor", Double.class), 0.001);
        assertTrue(consoleText().contains("Speed set to ×2."));

        fireButton("speed3Btn");
        assertEquals(3.0, field("speedFactor", Double.class), 0.001);
        assertTrue(consoleText().contains("Speed set to ×3."));

        fireButton("speed1Btn");
        assertEquals(1.0, field("speedFactor", Double.class), 0.001);
        assertTrue(consoleText().contains("Speed set to ×1."));
    }

    /** Verifies zoom-in/out/reset controls update viewport zoom and reset log messaging. */
    @Test
    void zoom_buttons_and_origin_reset_update_zoom_and_console() {
        double initialZoom = field("zoom", Double.class);

        fireButtonByText("+");
        assertTrue(field("zoom", Double.class) > initialZoom);

        fireButtonByText("-");
        assertEquals(initialZoom, field("zoom", Double.class), 0.0001);

        fireButtonByText("⊙");
        assertEquals(1.0, field("zoom", Double.class), 0.0001);
        assertTrue(consoleText().contains("Viewport reset to origin."));
    }

    /** Verifies sidebar/console toggle menu items collapse and restore split-pane dividers. */
    @Test
    void sidebar_and_console_toggles_collapse_and_restore_split_panes() {
        CheckMenuItem sidebarItem = field("toggleSidebarItem", CheckMenuItem.class);
        CheckMenuItem consoleItem = field("toggleConsoleItem", CheckMenuItem.class);
        SplitPane mainSplit = field("mainSplitPane", SplitPane.class);
        SplitPane consoleSplit = field("viewportConsoleSplit", SplitPane.class);

        interact(() -> {
            sidebarItem.setSelected(false);
            invokePrivate("onToggleSidebar", new Class<?>[0]);
            consoleItem.setSelected(false);
            invokePrivate("onToggleConsole", new Class<?>[0]);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0.0, mainSplit.getDividerPositions()[0], 0.001);
        assertEquals(1.0, consoleSplit.getDividerPositions()[0], 0.02);

        interact(() -> {
            sidebarItem.setSelected(true);
            invokePrivate("onToggleSidebar", new Class<?>[0]);
            consoleItem.setSelected(true);
            invokePrivate("onToggleConsole", new Class<?>[0]);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0.17, mainSplit.getDividerPositions()[0], 0.08);
        assertEquals(0.83, consoleSplit.getDividerPositions()[0], 0.08);
    }

    /** Verifies restart with valid engine resets run state and rotates run identifier. */
    @Test
    void restart_with_engine_resets_tick_progress_status_and_updates_run_id() {
        SimulationEngine engine = emptyEngine();
        loadScreenWith(engine, null, 30, 30);
        Label simStatus = installOptionalStatusLabel();
        UUID originalRunId = engine.getRunId();

        invokeOnFx("onNextFrame", new Class<?>[0]);
        assertEquals("TICK 1", tickLabel().getText());

        fireButtonByText("↺");

        assertEquals("READY", simStatus.getText());
        assertEquals("TICK 0", tickLabel().getText());
        assertEquals(0.0, progressBar().getProgress(), 0.001);
        assertNotEquals(originalRunId, engine.getRunId());
        assertTrue(consoleText().contains("Simulation reset."));
    }

    /** Verifies restart surfaces error state when saved config path reload is invalid. */
    @Test
    void restart_with_bad_config_path_reports_error_state() throws Exception {
        Path malformed = tempDir.resolve("bad-restart.json");
        Files.writeString(malformed, "{ not valid json");
        SimulationEngine engine = emptyEngine();
        loadScreenWith(engine, malformed.toString(), 30, 30);
        Label simStatus = installOptionalStatusLabel();

        invokeOnFx("onNextFrame", new Class<?>[0]);

        fireButtonByText("↺");

        assertEquals("ERROR", simStatus.getText());
        assertEquals("TICK 0", tickLabel().getText());
        assertEquals(0.0, progressBar().getProgress(), 0.001);
        assertTrue(consoleText().contains("Simulation reset failed"));
    }

    /** Verifies reset after Results → Editor reloads the saved editor baseline instead of the original config. */
    @Test
    void restart_after_results_round_trip_uses_editor_baseline_snapshot() throws Exception {
        Path originalConfig = tempDir.resolve("original-empty.json");
        Path baselineConfig = tempDir.resolve("editor-baseline.json");

        SimulationEngine originalEngine = new SimulationEngine(
                new Map(4, 4),
                new Robot[]{},
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );
        originalEngine.configSaving(originalConfig.toString());

        Map editedMap = new Map(4, 4);
        Robot placedRobot = new Robot("placed_bot", new Vector2D(2, 1));
        editedMap.addEntity(placedRobot);
        SimulationEngine editedEngine = new SimulationEngine(
                editedMap,
                new Robot[]{ placedRobot },
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );
        editedEngine.configSaving(baselineConfig.toString());

        loadScreenWith(editedEngine, originalConfig.toString(), baselineConfig.toString(), 30, 30);

        assertEquals(baselineConfig.toString(), field("initialSnapshotPath", String.class));

        fireButtonByText("RESULTS");
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(lookup("#robotStatsTable").queryAs(TableView.class));

        fireButtonByText("RETURN TO EDITOR");
        WaitForAsyncUtils.waitForFxEvents();

        interact(this::syncControllerFromNavigator);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(baselineConfig.toString(), field("initialSnapshotPath", String.class));

        fireButtonByText("↺");

        assertEquals(1, AppState.getEngine().getMap().getEntities().size());
        assertTrue(AppState.getEngine().getMap().getEntities().stream()
                .anyMatch(entity -> "placed_bot".equals(entity.getName())));
        assertEquals("TICK 0", tickLabel().getText());
    }

    /** Verifies results navigation and back-navigation route to expected real screens. */
    @Test
    void results_tab_and_back_button_navigate_through_real_screens() {
        fireButtonByText("RESULTS");
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(lookup("#robotStatsTable").queryAs(TableView.class));

        loadScreenWith(emptyEngine(), null, 30, 30);
        assertTrue(AppState.hasEngine());

        fireButtonByText("← BACK");
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(AppState.hasEngine());
        assertNotNull(lookup("#mapCombo").query());
    }

    /** Verifies editor tab activation updates tab style state without navigation side effects. */
    @Test
    void editor_tab_keeps_editor_active_without_navigation() {
        Button editor = field("editorTabBtn", Button.class);
        Button results = field("resultsTabBtn", Button.class);

        fireButton("editorTabBtn");

        assertEquals(List.of("tab-btn-active"), editor.getStyleClass());
        assertEquals(List.of("tab-btn"), results.getStyleClass());
    }

    /** Verifies single-click object tile selection logs selected type without opening dialogs. */
    @Test
    void add_object_single_click_logs_selected_type_without_opening_dialog() {
        Button robotTile = objectTile("ROBOT");

        interact(() -> Event.fireEvent(robotTile, clickEvent(1)));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(consoleText().contains("Selected object type: ROBOT."));
    }

    /** Verifies cleanup stops animation/tip loops while preserving active AppState engine. */
    @Test
    void cleanup_stops_tip_and_animation_timelines() {
        EngineFixture fixture = loadDiverseEngine();
        invokeOnFx("onNextFrame", new Class<?>[0]);

        assertNotNull(field("tipRotationLoop", javafx.animation.Timeline.class));
        assertNotNull(field("animTimeline", javafx.animation.Timeline.class));

        interact(controller::cleanup);
        WaitForAsyncUtils.waitForFxEvents();

        assertNull(field("tipRotationLoop", javafx.animation.Timeline.class));
        assertNull(field("animTimeline", javafx.animation.Timeline.class));
        assertFalse(field("animating", Boolean.class));
        assertSame(fixture.engine, AppState.getEngine());
    }

    /** Clears console through UI button to mirror user-driven behavior. */
    private void clearConsoleThroughUi() {
        Button clear = field("clearConsoleButton", Button.class);
        interact(clear::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Waits for console to become empty to reduce flakiness from async logging.
     *
     * @throws TimeoutException if console is not emptied within timeout
     */
    private void assertConsoleEventuallyEmpty() throws TimeoutException {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> consoleText().isEmpty());
    }

    /**
     * Loads/reloads screen using provided engine/config and canvas dimensions.
     */
    private void loadScreenWith(SimulationEngine engine, String configPath, int canvasWidth, int canvasHeight) {
        loadScreenWith(engine, configPath, null, canvasWidth, canvasHeight);
    }

    /**
     * Loads/reloads screen using provided engine/config/baseline and canvas dimensions.
     */
    private void loadScreenWith(SimulationEngine engine, String configPath, String editorBaselinePath,
                                int canvasWidth, int canvasHeight) {
        interact(() -> {
            try {
                AppState.clear();
                AppState.setCanvasDimensions(canvasWidth, canvasHeight);
                if (engine != null) {
                    AppState.setEngine(engine);
                }
                if (configPath != null) {
                    AppState.setConfigPath(configPath);
                }
                if (editorBaselinePath != null) {
                    AppState.setEditorBaselinePath(editorBaselinePath);
                }
                loadSceneFromCurrentAppState();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Loads simulation FXML and binds the controller to the shared stage.
     *
     * @throws IOException if FXML cannot be loaded
     */
    private void loadSceneFromCurrentAppState() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/openrobotics/fxml/SimulationScreen.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root, 1200, 700));
        stage.show();
    }

    /** Refreshes the cached controller after navigation recreates the simulation screen. */
    private void syncControllerFromNavigator() {
        controller = assertInstanceOf(SimulationController.class, ScreenNavigator.getCurrentController());
    }

    /** Builds and loads a representative fixture containing multiple entity types and one task. */
    private EngineFixture loadDiverseEngine() {
        EngineFixture fixture = diverseFixture();
        loadScreenWith(fixture.engine, null, 6, 5);
        return fixture;
    }

    /** Creates a deterministic mixed-entity engine fixture for outliner/properties behavior tests. */
    private EngineFixture diverseFixture() {
        Map map = new Map(5, 5);
        Robot robot = new Robot("robot_1", new Vector2D(0, 0));
        robot.setNav(new GreedyNavigationStrategy(11L));
        robot.setSensor(new ProximitySensor());
        Rack rack = new Rack("rack_1", new Vector2D(1, 0));
        ChargingStation charger = new ChargingStation("charger_1", new Vector2D(0, 1));
        DeliveryStation station = new DeliveryStation("station_1", new Vector2D(3, 0));
        Obstacle obstacle = new Obstacle("wall_1", new Vector2D(2, 2));
        map.addEntity(robot);
        map.addEntity(rack);
        map.addEntity(charger);
        map.addEntity(station);
        map.addEntity(obstacle);

        Dispatcher dispatcher = new Dispatcher();
        Task task = new Task(99, new Vector2D(1, 0), new Vector2D(3, 0), 7);
        dispatcher.addTask(task);

        SimulationEngine engine = new SimulationEngine(map, new Robot[]{robot}, dispatcher, CoordinationPolicy.noOp());
        return new EngineFixture(engine, robot, rack, charger, station, obstacle, task);
    }

    /** Creates a minimal engine suitable for transport/control-flow tests. */
    private SimulationEngine emptyEngine() {
        Map map = new Map(3, 3);
        Robot robot = new Robot("idle_bot", new Vector2D(0, 0));
        robot.setNav(new GreedyNavigationStrategy(1L));
        robot.setSensor(new ProximitySensor());
        map.addEntity(robot);
        return new SimulationEngine(map, new Robot[]{robot}, new Dispatcher(), CoordinationPolicy.noOp());
    }

    /** Creates an entity via controller factory path for a given user-facing type token. */
    private MapEntity createdEntity(String type, int x, int y) {
        return (MapEntity) invokePrivate(
                "createEntityFromType",
                new Class<?>[]{String.class, int.class, int.class, String.class},
                type,
                x,
                y,
                type.toLowerCase() + "_test"
        );
    }

    /** Proxies private placement rule check to validate occupancy constraints in tests. */
    private boolean canPlace(MapEntity entity, int x, int y, MapEntity ignore) {
        return (boolean) invokePrivate(
                "canPlaceEntityAt",
                new Class<?>[]{MapEntity.class, int.class, int.class, MapEntity.class},
                entity,
                x,
                y,
                ignore
        );
    }

    /** Fires a button by controller field name. */
    private void fireButton(String fieldName) {
        Button button = field(fieldName, Button.class);
        interact(button::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Fires the first visible button matching display text. */
    private void fireButtonByText(String text) {
        Button button = buttonWithText(text);
        interact(button::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Finds a button by displayed text in current scene graph. */
    private Button buttonWithText(String text) {
        return descendants(stage.getScene().getRoot()).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No button with text: " + text));
    }

    /** Finds an object palette tile button by userData marker. */
    private Button objectTile(String userData) {
        return descendants(stage.getScene().getRoot()).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> userData.equals(button.getUserData()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No object tile with userData: " + userData));
    }

    /** Creates a synthetic primary-click event with specified click count. */
    private MouseEvent clickEvent(int clickCount) {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                clickCount,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null
        );
    }

    /** Returns a flattened list of node descendants including the root node itself. */
    private List<Node> descendants(Node node) {
        List<Node> result = new ArrayList<>();
        result.add(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                result.addAll(descendants(child));
            }
        }
        return result;
    }

    /** Aggregates labeled text from properties panel for content assertions. */
    private String propertiesPanelText() {
        Parent panel = field("propertiesPanel", Parent.class);
        return descendants(panel).stream()
                .filter(Labeled.class::isInstance)
                .map(Labeled.class::cast)
                .map(Labeled::getText)
                .collect(Collectors.joining("\n"));
    }

    /** Finds a TextField in properties panel with exact current value. */
    private TextField textFieldWithValue(String value) {
        Parent panel = field("propertiesPanel", Parent.class);
        return descendants(panel).stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(textField -> value.equals(textField.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No TextField with value: " + value));
    }

    /** Returns outliner list view from private controller field. */
    @SuppressWarnings("unchecked")
    private ListView<String> outliner() {
        return (ListView<String>) field("outlinerListView", ListView.class);
    }

    /** Returns console text area reference. */
    private TextArea console() {
        return field("consoleArea", TextArea.class);
    }

    /** Returns current console contents. */
    private String consoleText() {
        return console().getText();
    }

    /** Returns tick display label. */
    private Label tickLabel() {
        return field("tickDisplayLabel", Label.class);
    }

    /** Returns simulation progress bar. */
    private ProgressBar progressBar() {
        return field("simProgressBar", ProgressBar.class);
    }

    /** Installs optional sim-status label used by some controller branches. */
    private Label installOptionalStatusLabel() {
        Label label = new Label();
        installPrivateField("simStatusLabel", label);
        return label;
    }

    /** Installs a private field value on FX thread. */
    private void installPrivateField(String name, Object value) {
        interact(() -> setField(name, value));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Invokes a private controller method on FX thread and returns its result. */
    private Object invokeOnFx(String name, Class<?>[] parameterTypes, Object... args) {
        AtomicReference<Object> result = new AtomicReference<>();
        interact(() -> result.set(invokePrivate(name, parameterTypes, args)));
        WaitForAsyncUtils.waitForFxEvents();
        return result.get();
    }

    /** Invokes a private controller method via reflection. */
    private Object invokePrivate(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = SimulationController.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(controller, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Reads a private controller field and casts it to target type. */
    private <T> T field(String name, Class<T> type) {
        try {
            Field field = SimulationController.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(controller));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Writes a private controller field through reflection. */
    private void setField(String name, Object value) {
        try {
            Field field = SimulationController.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(controller, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Engine test double whose tick method reports immediate completion. */
    private static class CompletingEngine extends SimulationEngine {
        CompletingEngine() {
            super(new Map(2, 2), new Robot[]{new Robot("bot", new Vector2D(0, 0))}, new Dispatcher(), CoordinationPolicy.noOp());
        }

        @Override
        public boolean tick() {
            return false;
        }
    }

    /** Engine test double that throws on snapshot/config save attempts. */
    private static class UnsavableEngine extends SimulationEngine {
        UnsavableEngine() {
            super(new Map(2, 2), new Robot[]{new Robot("bot", new Vector2D(0, 0))}, new Dispatcher(), CoordinationPolicy.noOp());
        }

        @Override
        public void configSaving(String path) throws IOException {
            throw new IOException("snapshot failed");
        }
    }

    /** Container for a reusable diverse engine fixture and key entity references. */
    private static class EngineFixture {
        final SimulationEngine engine;
        final Robot robot;
        final Rack rack;
        final ChargingStation charger;
        final DeliveryStation station;
        final Obstacle obstacle;
        final Task task;

        EngineFixture(
                SimulationEngine engine,
                Robot robot,
                Rack rack,
                ChargingStation charger,
                DeliveryStation station,
                Obstacle obstacle,
                Task task
        ) {
            this.engine = engine;
            this.robot = robot;
            this.rack = rack;
            this.charger = charger;
            this.station = station;
            this.obstacle = obstacle;
            this.task = task;
        }
    }

    /** Verifies intersection tile is available for traffic-rules policy engines. */
    @Test
    void intersection_tile_visible_for_traffic_rules_engine() {
        reloadSimulationWithEngine(buildEngine(new TrafficRulesPolicy(new java.util.HashSet<>())));

        Button intersectionButton = lookup("#intersectionObjectTile").queryAs(Button.class);
        assertTrue(intersectionButton.isVisible());
        assertTrue(intersectionButton.isManaged());
    }

    /** Verifies intersection tile is hidden for reservation policy engines. */
    @Test
    void intersection_tile_hidden_for_reservation_policy_engine() {
        reloadSimulationWithEngine(buildEngine(new ReservationKPolicy(3)));

        Button intersectionButton = lookup("#intersectionObjectTile").queryAs(Button.class);
        assertFalse(intersectionButton.isVisible());
        assertFalse(intersectionButton.isManaged());
    }

    /** Verifies intersection tile is hidden for NO_OP policy engines. */
    @Test
    void intersection_tile_hidden_for_no_op_engine() {
        reloadSimulationWithEngine(buildEngine(CoordinationPolicy.noOp()));

        Button intersectionButton = lookup("#intersectionObjectTile").queryAs(Button.class);
        assertFalse(intersectionButton.isVisible());
        assertFalse(intersectionButton.isManaged());
    }
}
