package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotConfig;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import com.openrobotics.task.Task;
import com.openrobotics.util.ScreenNavigator;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SetupControllerTest extends ApplicationTest {

    @TempDir
    Path tempDir;

    private Stage stage;
    private SetupController controller;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        ScreenNavigator.setPrimaryStage(stage);
        loadSetupScene();
    }

    @BeforeEach
    void resetSetupScene() {
        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
        loadSetupSceneOnFx();
    }

    @AfterEach
    void clearSharedState() {
        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
    }

    @Test
    void initialize_sets_all_visible_defaults_and_available_options() {
        assertAll(
                () -> assertEquals("empty", mapCombo().getValue()),
                () -> assertEquals(List.of("empty", "baseline_small", "narrow_aisles", "many_intersections", "random_map"), mapCombo().getItems()),
                () -> assertEquals("NONE", policyCombo().getValue()),
                () -> assertEquals(List.of("NONE", "TRAFFIC_RULES", "RESERVATION_K"), policyCombo().getItems()),
                () -> assertTrue(reservationKSpinner().isDisabled()),
                () -> assertEquals(3, reservationKSpinner().getValue()),
                () -> assertEquals("10", textField("maxTasksField").getText()),
                () -> assertEquals("42", textField("workloadSeedField").getText()),
                () -> assertEquals("30000", textField("maxTicksField").getText()),
                () -> assertEquals("experiment_1", textField("runNameField").getText()),
                () -> assertEquals(30, canvasWidthSpinner().getValue()),
                () -> assertEquals(30, canvasHeightSpinner().getValue()),
                () -> assertFalse(field("mapSeedRow", HBox.class).isVisible()),
                () -> assertFalse(field("canvasWarnLabel", Label.class).isVisible()),
                () -> assertTrue(field("configListView", ListView.class).getItems().size() >= 1)
        );
    }

    @Test
    void random_map_selection_shows_seed_row_and_reset_clears_seed() {
        TextField seedField = textField("randomSeedField");

        interact(() -> {
            mapCombo().setValue("random_map");
            seedField.setText("123");
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(field("mapSeedRow", HBox.class).isVisible());
        assertEquals(30, canvasWidthSpinner().getValue());
        assertEquals(30, canvasHeightSpinner().getValue());

        invokeOnFx("onResetRandomSeed", new Class<?>[0]);

        assertEquals("", seedField.getText());
    }

    @Test
    void builtin_map_selection_auto_sizes_canvas_and_warns_at_minimum() {
        interact(() -> mapCombo().setValue("baseline_small"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(18, canvasWidthSpinner().getValue());
        assertEquals(12, canvasHeightSpinner().getValue());
        assertTrue(field("canvasWarnLabel", Label.class).isVisible());
        assertTrue(field("canvasWarnLabel", Label.class).getText().contains("Min: 18 width"));
        assertEquals(18, AppState.getCanvasWidthTiles());
        assertEquals(12, AppState.getCanvasHeightTiles());

        interact(() -> {
            canvasWidthSpinner().getValueFactory().setValue(24);
            canvasHeightSpinner().getValueFactory().setValue(16);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(field("canvasWarnLabel", Label.class).isVisible());
    }

    @Test
    void reservation_spinner_enables_only_for_reservation_k_policy() {
        interact(() -> policyCombo().setValue("RESERVATION_K"));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(reservationKSpinner().isDisabled());

        interact(() -> policyCombo().setValue("TRAFFIC_RULES"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(reservationKSpinner().isDisabled());

        interact(() -> policyCombo().setValue("NONE"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(reservationKSpinner().isDisabled());
    }

    @Test
    void reset_handlers_restore_defaults_for_visible_and_optional_fields() {
        installRobotPhysicsFields("150", "35", "9", "3");
        interact(() -> {
            mapCombo().setValue("many_intersections");
            textField("randomSeedField").setText("99");
            policyCombo().setValue("RESERVATION_K");
            reservationKSpinner().getValueFactory().setValue(12);
            textField("maxTasksField").setText("27");
            textField("workloadSeedField").setText("12345");
            textField("maxTicksField").setText("77");
            textField("runNameField").setText("custom");
            canvasWidthSpinner().getValueFactory().setValue(40);
            canvasHeightSpinner().getValueFactory().setValue(30);
        });

        invokeOnFx("onResetMap", new Class<?>[0]);
        invokeOnFx("onResetRandomSeed", new Class<?>[0]);
        invokeOnFx("onResetPolicy", new Class<?>[0]);
        invokeOnFx("onResetReservationK", new Class<?>[0]);
        invokeOnFx("onResetMaxTasks", new Class<?>[0]);
        invokeOnFx("onResetWorkloadSeed", new Class<?>[0]);
        invokeOnFx("onResetMaxTicks", new Class<?>[0]);
        invokeOnFx("onResetRunName", new Class<?>[0]);
        invokeOnFx("onResetCanvasTiles", new Class<?>[0]);
        invokeOnFx("onResetBatteryCapacity", new Class<?>[0]);
        invokeOnFx("onResetLowBatteryThreshold", new Class<?>[0]);
        invokeOnFx("onResetChargePerTick", new Class<?>[0]);
        invokeOnFx("onResetEnergyPerMove", new Class<?>[0]);

        assertAll(
                () -> assertEquals("empty", mapCombo().getValue()),
                () -> assertEquals("", textField("randomSeedField").getText()),
                () -> assertEquals("NONE", policyCombo().getValue()),
                () -> assertEquals(3, reservationKSpinner().getValue()),
                () -> assertEquals("10", textField("maxTasksField").getText()),
                () -> assertEquals("42", textField("workloadSeedField").getText()),
                () -> assertEquals("30000", textField("maxTicksField").getText()),
                () -> assertEquals("experiment_1", textField("runNameField").getText()),
                () -> assertEquals(16, canvasWidthSpinner().getValue()),
                () -> assertEquals(10, canvasHeightSpinner().getValue()),
                () -> assertEquals("100", field("batteryCapacityField", TextField.class).getText()),
                () -> assertEquals("20", field("lowBatteryField", TextField.class).getText()),
                () -> assertEquals("5", field("chargePerTickField", TextField.class).getText()),
                () -> assertEquals("1", field("energyPerMoveField", TextField.class).getText())
        );
    }

    @Test
    void validate_rejects_missing_map_only_when_no_engine_is_loaded() {
        interact(() -> mapCombo().getSelectionModel().clearSelection());
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse((boolean) invokePrivate("validate", new Class<?>[0]));
        assertEquals("⚠ Please select a map or load a config file.", statusLabel().getText());

        AppState.setEngine(emptyEngine());

        assertTrue((boolean) invokePrivate("validate", new Class<?>[0]));
        assertEquals("", statusLabel().getText());
    }

    @Test
    void validate_rejects_bad_max_tick_values_and_accepts_positive_integer() {
        TextField maxTicks = textField("maxTicksField");

        interact(() -> maxTicks.setText("abc"));
        assertFalse((boolean) invokePrivate("validate", new Class<?>[0]));
        assertEquals("⚠ Max ticks must be a positive integer.", statusLabel().getText());

        interact(() -> maxTicks.setText("0"));
        assertFalse((boolean) invokePrivate("validate", new Class<?>[0]));

        interact(() -> maxTicks.setText("-5"));
        assertFalse((boolean) invokePrivate("validate", new Class<?>[0]));

        interact(() -> maxTicks.setText("1"));
        assertTrue((boolean) invokePrivate("validate", new Class<?>[0]));
        assertEquals("", statusLabel().getText());
    }

    @Test
    void buildCoordinationPolicy_returns_expected_policy_implementations() {
        assertSame(CoordinationPolicy.noOp(), invokePrivate("buildCoordinationPolicy", new Class<?>[0]));

        interact(() -> policyCombo().setValue("TRAFFIC_RULES"));
        assertInstanceOf(TrafficRulesPolicy.class, invokePrivate("buildCoordinationPolicy", new Class<?>[0]));

        interact(() -> {
            policyCombo().setValue("RESERVATION_K");
            reservationKSpinner().getValueFactory().setValue(7);
        });
        ReservationKPolicy policy = (ReservationKPolicy) invokePrivate("buildCoordinationPolicy", new Class<?>[0]);
        assertEquals(7, policy.getK());

        interact(() -> policyCombo().getSelectionModel().clearSelection());
        assertSame(CoordinationPolicy.noOp(), invokePrivate("buildCoordinationPolicy", new Class<?>[0]));
    }

    @Test
    void parse_helpers_trim_values_and_return_fallbacks_for_bad_input() {
        assertEquals(12, invokePrivate("parseIntSafe", new Class<?>[]{String.class, int.class}, " 12 ", 3));
        assertEquals(3, invokePrivate("parseIntSafe", new Class<?>[]{String.class, int.class}, "bad", 3));
        assertEquals(4.5f, (float) invokePrivate("parseFloatSafe", new Class<?>[]{String.class, float.class}, " 4.5 ", 1.0f), 0.001f);
        assertEquals(1.0f, (float) invokePrivate("parseFloatSafe", new Class<?>[]{String.class, float.class}, "bad", 1.0f), 0.001f);
        assertEquals(2.0f, (float) invokePrivate("parseOptionalFloatField", new Class<?>[]{TextField.class, float.class}, null, 2.0f), 0.001f);
        assertEquals(9.0f, (float) invokePrivate("parseOptionalFloatField", new Class<?>[]{TextField.class, float.class}, new TextField("9"), 2.0f), 0.001f);
    }

    @Test
    void builtin_map_builders_return_expected_shapes_and_bounds() {
        assertNull(invokePrivate("buildBuiltinMap", new Class<?>[]{String.class}, "empty"));
        assertNull(invokePrivate("buildBuiltinMap", new Class<?>[]{String.class}, "unknown"));

        Map baseline = (Map) invokePrivate("buildBuiltinMap", new Class<?>[]{String.class}, "baseline_small");
        Map narrow = (Map) invokePrivate("buildBuiltinMap", new Class<?>[]{String.class}, "narrow_aisles");
        Map intersections = (Map) invokePrivate("buildBuiltinMap", new Class<?>[]{String.class}, "many_intersections");

        assertAll(
                () -> assertEquals(16, baseline.getWidth()),
                () -> assertEquals(10, baseline.getHeight()),
                () -> assertEquals(22, narrow.getWidth()),
                () -> assertEquals(14, narrow.getHeight()),
                () -> assertEquals(26, intersections.getWidth()),
                () -> assertEquals(18, intersections.getHeight()),
                () -> assertTrue(countEntities(baseline, Rack.class) > 0),
                () -> assertTrue(countEntities(baseline, Obstacle.class) > 0),
                () -> assertEquals(1, countEntities(baseline, ChargingStation.class)),
                () -> assertEquals(1, countEntities(baseline, DeliveryStation.class))
        );

        int[] bounds = (int[]) invokePrivate("computeEntityBounds", new Class<?>[]{Map.class}, baseline);
        assertEquals(List.of(0, 0, 15, 9), List.of(bounds[0], bounds[1], bounds[2], bounds[3]));
    }

    @Test
    void builtin_map_in_canvas_preserves_entity_types_and_offsets_into_canvas() {
        Map empty = (Map) invokePrivate("buildBuiltinMapInCanvas", new Class<?>[]{String.class, int.class, int.class}, "empty", 8, 6);
        Map baseline = (Map) invokePrivate("buildBuiltinMapInCanvas", new Class<?>[]{String.class, int.class, int.class}, "baseline_small", 20, 14);

        assertTrue(empty.getEntities().isEmpty());
        assertEquals(8, empty.getWidth());
        assertEquals(6, empty.getHeight());
        assertEquals(20, baseline.getWidth());
        assertEquals(14, baseline.getHeight());
        assertEquals(new Vector2D(1, 5), onlyEntity(baseline, ChargingStation.class).getPosition());
        assertEquals(new Vector2D(16, 5), onlyEntity(baseline, DeliveryStation.class).getPosition());
        assertTrue(countEntities(baseline, Rack.class) > 0);
        assertTrue(countEntities(baseline, Obstacle.class) > 0);
    }

    @Test
    void random_map_builder_is_seeded_and_keeps_core_entities_in_expected_regions() {
        Map first = (Map) invokePrivate("buildRandomMap", new Class<?>[]{int.class, int.class, long.class}, 16, 10, 123L);
        Map second = (Map) invokePrivate("buildRandomMap", new Class<?>[]{int.class, int.class, long.class}, 16, 10, 123L);

        assertEquals(entitySignature(first), entitySignature(second));
        assertEquals(16, first.getWidth());
        assertEquals(10, first.getHeight());
        assertEquals(1, countEntities(first, ChargingStation.class));
        assertEquals(1, countEntities(first, DeliveryStation.class));
        assertTrue(countEntities(first, Rack.class) >= 2);
        assertTrue(countEntities(first, Obstacle.class) >= 1);
        assertEquals(0, onlyEntity(first, ChargingStation.class).getPosition().getX());
        assertEquals(15, onlyEntity(first, DeliveryStation.class).getPosition().getX());
    }

    @Test
    void findSpawnTile_returns_nearest_available_traversable_tile_or_center_fallback() {
        Map map = new Map(3, 3);
        map.addEntity(new Obstacle("blocked", new Vector2D(1, 1)));
        Set<String> occupied = new HashSet<>(Set.of("1,0", "0,1"));

        Vector2D spawn = (Vector2D) invokePrivate(
                "findSpawnTile",
                new Class<?>[]{Vector2D.class, Map.class, Set.class},
                new Vector2D(1, 1),
                map,
                occupied
        );

        assertTrue(map.isTraversable(spawn));
        assertFalse(occupied.contains(spawn.getX() + "," + spawn.getY()));

        Set<String> allOccupied = new HashSet<>();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                allOccupied.add(x + "," + y);
            }
        }

        Vector2D fallback = (Vector2D) invokePrivate(
                "findSpawnTile",
                new Class<?>[]{Vector2D.class, Map.class, Set.class},
                new Vector2D(1, 1),
                map,
                allOccupied
        );

        assertEquals(new Vector2D(1, 1), fallback);
    }

    @Test
    void generateFixedTasks_uses_racks_and_delivery_stations_when_available() {
        Map map = new Map(4, 3);
        map.addEntity(new Rack("rack", new Vector2D(1, 1)));
        map.addEntity(new DeliveryStation("delivery", new Vector2D(3, 1)));
        Dispatcher dispatcher = new Dispatcher();

        invokePrivate("generateFixedTasks", new Class<?>[]{Map.class, long.class, int.class, Dispatcher.class}, map, 1L, 3, dispatcher);

        assertEquals(3, dispatcher.getPendingTaskCount());
        for (Task task : dispatcher.getAllQueuedTasks()) {
            assertEquals(new Vector2D(1, 1), task.getPickupLocation());
            assertEquals(new Vector2D(3, 1), task.getDropoffLocation());
        }
    }

    @Test
    void generateFixedTasks_falls_back_to_automatic_task_generation() {
        Map map = new Map(3, 3);
        map.addEntity(new DeliveryStation("delivery", new Vector2D(2, 2)));
        Dispatcher dispatcher = new Dispatcher();

        invokePrivate("generateFixedTasks", new Class<?>[]{com.openrobotics.map.Map.class, long.class, int.class, Dispatcher.class}, map, 2L, 4, dispatcher);

        assertEquals(0, dispatcher.getPendingTaskCount());
        assertTrue(dispatcher.getAllQueuedTasks().stream().allMatch(task -> task.getDropoffLocation().equals(new Vector2D(2, 2))));
        assertTrue(dispatcher.getAllQueuedTasks().stream().noneMatch(task -> task.getPickupLocation().equals(new Vector2D(2, 2))));
    }

    @Test
    void generateFixedTasks_does_not_create_floor_pickups_for_unreachable_racks() {
        Map map = new Map(3, 3);
        map.addEntity(new Rack("boxed-in", new Vector2D(1, 1)));
        map.addEntity(new Obstacle("left", new Vector2D(0, 1)));
        map.addEntity(new Obstacle("right", new Vector2D(2, 1)));
        map.addEntity(new Obstacle("up", new Vector2D(1, 0)));
        map.addEntity(new Obstacle("down", new Vector2D(1, 2)));
        map.addEntity(new DeliveryStation("delivery", new Vector2D(2, 2)));
        Dispatcher dispatcher = new Dispatcher();

        invokePrivate("generateFixedTasks", new Class<?>[]{Map.class, long.class, int.class, Dispatcher.class}, map, 3L, 5, dispatcher);

        assertEquals(0, dispatcher.getPendingTaskCount());
    }

    @Test
    void buildEngineFromSetup_uses_field_values_defaults_and_robot_config() {
        installRobotPhysicsFields("150.5", "35.5", "9.5", "3.5");
        interact(() -> {
            mapCombo().setValue("baseline_small");
            policyCombo().setValue("RESERVATION_K");
            reservationKSpinner().getValueFactory().setValue(5);
            textField("maxTasksField").setText("4");
            textField("workloadSeedField").setText("123");
            textField("maxTicksField").setText("77");
            textField("runNameField").setText("run_from_fields");
        });

        SimulationEngine engine = (SimulationEngine) invokePrivate("buildEngineFromSetup", new Class<?>[0]);
        RobotConfig config = engine.getRobotConfig();

        assertEquals(18, engine.getMap().getWidth());
        assertEquals(12, engine.getMap().getHeight());
        assertEquals(77, engine.getMaxTicks());
        assertEquals(123L, engine.getSeed());
        assertTrue(engine.getCoordinationPolicy().contains(ReservationKPolicy.class.getName()));
        assertEquals(4, engine.getDispatcher().getTotalTasksAdded());
        assertEquals(150.5f, config.batteryCapacity, 0.001f);
        assertEquals(35.5f, config.lowBatteryThreshold, 0.001f);
        assertEquals(9.5f, config.chargePerTick, 0.001f);
        assertEquals(3.5f, config.energyPerMove, 0.001f);
    }

    @Test
    void buildEngineFromSetup_falls_back_for_invalid_numeric_fields_and_blank_run_name() {
        installRobotPhysicsFields("bad", "bad", "bad", "bad");
        interact(() -> {
            mapCombo().setValue("empty");
            textField("maxTasksField").setText("bad");
            textField("workloadSeedField").setText("bad");
            textField("maxTicksField").setText("bad");
            textField("runNameField").setText("   ");
        });

        SimulationEngine engine = (SimulationEngine) invokePrivate("buildEngineFromSetup", new Class<?>[0]);
        RobotConfig config = engine.getRobotConfig();

        assertEquals(30, engine.getMap().getWidth());
        assertEquals(30, engine.getMap().getHeight());
        assertEquals(30000, engine.getMaxTicks());
        assertEquals(42L, engine.getSeed());
        assertEquals(0, engine.getDispatcher().getTotalTasksAdded());
        assertEquals(100.0f, config.batteryCapacity, 0.001f);
        assertEquals(20.0f, config.lowBatteryThreshold, 0.001f);
        assertEquals(5.0f, config.chargePerTick, 0.001f);
        assertEquals(1.0f, config.energyPerMove, 0.001f);
    }

    @Test
    void buildEngineFromSetup_random_map_honors_explicit_map_seed() {
        interact(() -> {
            mapCombo().setValue("random_map");
            canvasWidthSpinner().getValueFactory().setValue(16);
            canvasHeightSpinner().getValueFactory().setValue(10);
            textField("randomSeedField").setText("999");
            textField("workloadSeedField").setText("111");
            textField("maxTasksField").setText("3");
        });

        SimulationEngine first = (SimulationEngine) invokePrivate("buildEngineFromSetup", new Class<?>[0]);
        SimulationEngine second = (SimulationEngine) invokePrivate("buildEngineFromSetup", new Class<?>[0]);

        assertEquals(entitySignature(first.getMap()), entitySignature(second.getMap()));
        assertEquals(111L, first.getSeed());
        assertEquals(3, first.getDispatcher().getTotalTasksAdded());
        assertEquals(1, countEntities(first.getMap(), ChargingStation.class));
        assertEquals(1, countEntities(first.getMap(), DeliveryStation.class));
    }

    @Test
    void buildEngineFromSetup_returns_null_and_status_when_map_selection_is_missing() {
        interact(() -> mapCombo().getSelectionModel().clearSelection());

        assertNull(invokePrivate("buildEngineFromSetup", new Class<?>[0]));
        assertEquals("⚠ Please select a map.", statusLabel().getText());
    }

    @Test
    void loadConfigFile_rejects_null_missing_and_malformed_files() throws Exception {
        assertFalse((boolean) invokePrivate("loadConfigFile", new Class<?>[]{File.class}, (Object) null));
        assertTrue(statusLabel().getText().contains("Selected file is not accessible"));

        File missing = tempDir.resolve("missing.json").toFile();
        assertFalse((boolean) invokePrivate("loadConfigFile", new Class<?>[]{File.class}, missing));
        assertTrue(statusLabel().getText().contains("Selected file is not accessible"));

        Path malformed = tempDir.resolve("malformed.json");
        Files.writeString(malformed, "{ not json");
        assertFalse((boolean) invokePrivate("loadConfigFile", new Class<?>[]{File.class}, malformed.toFile()));
        assertTrue(statusLabel().getText().contains("Parse failed"));
        assertFalse(AppState.hasEngine());
    }

    @Test
    void loadConfigFile_accepts_valid_saved_config_and_updates_app_state() throws Exception {
        File config = saveEngineConfig(tempDir.resolve("valid-config.json"), emptyEngine());

        assertTrue((boolean) invokePrivate("loadConfigFile", new Class<?>[]{File.class}, config));

        assertTrue(AppState.hasEngine());
        assertEquals(config.getAbsolutePath(), AppState.getConfigPath());
        assertTrue(statusLabel().getText().contains("✔ Loaded: valid-config.json"));
        assertEquals(3, AppState.getEngine().getMap().getWidth());
        assertEquals(3, AppState.getEngine().getMap().getHeight());
    }

    @Test
    void selecting_config_list_item_loads_config_clears_map_selection_and_disables_canvas_size() throws Exception {
        File configDir = new File("configs");
        configDir.mkdirs();
        File configFile = new File(configDir, "setup-controller-" + UUID.randomUUID() + ".json");
        try {
            saveEngineConfig(configFile.toPath(), emptyEngine());

            invokeOnFx("onRefreshFileList", new Class<?>[0]);
            interact(() -> field("configListView", ListView.class).getSelectionModel().select(configFile.getName()));
            WaitForAsyncUtils.waitForFxEvents();

            assertTrue(AppState.hasEngine());
            assertNull(mapCombo().getValue());
            assertTrue(canvasWidthSpinner().isDisabled());
            assertTrue(canvasHeightSpinner().isDisabled());
            assertTrue(statusLabel().getText().contains("✔ Loaded: " + configFile.getName()));
        } finally {
            Files.deleteIfExists(configFile.toPath());
        }
    }

    @Test
    void refresh_file_list_includes_json_files_and_ignores_non_json_files() throws Exception {
        File configDir = new File("configs");
        configDir.mkdirs();
        File json = new File(configDir, "setup-list-" + UUID.randomUUID() + ".json");
        File txt = new File(configDir, "setup-list-" + UUID.randomUUID() + ".txt");
        try {
            Files.writeString(json.toPath(), "{}");
            Files.writeString(txt.toPath(), "{}");

            invokeOnFx("onRefreshFileList", new Class<?>[0]);

            List<String> items = field("configListView", ListView.class).getItems();
            assertTrue(items.contains(json.getName()));
            assertFalse(items.contains(txt.getName()));
        } finally {
            Files.deleteIfExists(json.toPath());
            Files.deleteIfExists(txt.toPath());
        }
    }

    @Test
    void persistEngineToTempFile_stores_restart_config_path_and_reports_success() {
        SimulationEngine engine = emptyEngine();

        invokeOnFx("persistEngineToTempFile", new Class<?>[]{SimulationEngine.class}, engine);

        assertTrue(AppState.hasConfigPath());
        assertTrue(new File(AppState.getConfigPath()).isFile());
        assertTrue(statusLabel().getText().contains("✔ Config persisted for restart"));
    }

    @Test
    void persistEngineToTempFile_reports_error_and_clears_path_when_save_fails() {
        AppState.setConfigPath("existing.json");

        invokeOnFx("persistEngineToTempFile", new Class<?>[]{SimulationEngine.class}, new UnsavableEngine());

        assertNull(AppState.getConfigPath());
        assertTrue(statusLabel().getText().contains("⚠ Could not persist temp config for restart: cannot save"));
    }

    @Test
    void startSimulation_builds_engine_from_setup_values_persists_config_and_navigates() {
        interact(() -> {
            mapCombo().setValue("baseline_small");
            textField("maxTasksField").setText("2");
            textField("maxTicksField").setText("200");
        });

        invokeOnFx("onStartSimulation", new Class<?>[0]);

        assertTrue(AppState.hasEngine());
        assertTrue(AppState.hasConfigPath());
        assertEquals(2, AppState.getEngine().getDispatcher().getTotalTasksAdded());
        assertEquals(200, AppState.getEngine().getMaxTicks());
        assertNotNull(lookup("#warehouseCanvas").queryAs(Canvas.class));
    }

    @Test
    void startSimulation_rebuilds_existing_ui_built_engine_when_no_config_path_exists() {
        AppState.setEngine(emptyEngine());
        interact(() -> {
            mapCombo().setValue("baseline_small");
            textField("maxTasksField").setText("1");
        });

        invokeOnFx("onStartSimulation", new Class<?>[0]);

        assertTrue(AppState.hasEngine());
        assertTrue(AppState.hasConfigPath());
        assertEquals(18, AppState.getEngine().getMap().getWidth());
        assertEquals(1, AppState.getEngine().getDispatcher().getTotalTasksAdded());
        assertNotNull(lookup("#warehouseCanvas").queryAs(Canvas.class));
    }

    @Test
    void startSimulation_reports_config_reload_failure_when_saved_path_is_bad() throws Exception {
        Path malformed = tempDir.resolve("bad-start.json");
        Files.writeString(malformed, "{ not valid json");
        AppState.setConfigPath(malformed.toString());

        invokeOnFx("onStartSimulation", new Class<?>[0]);

        assertFalse(AppState.hasEngine());
        assertTrue(statusLabel().getText().contains("⚠ Config reload failed"));
        assertNotNull(lookup("#mapCombo").queryAs(ComboBox.class));
    }

    @Test
    void preview_blocked_sets_message_and_consumes_event() {
        MouseEvent event = mouseClick();

        invokeOnFx("onPreviewBlocked", new Class<?>[]{MouseEvent.class}, event);

        assertEquals("Map preview not interactive during setup.", statusLabel().getText());
        assertTrue(event.isConsumed());
    }

    @Test
    void menu_welcome_navigates_to_welcome_screen() {
        invokeOnFx("onMenuWelcome", new Class<?>[0]);

        assertNotNull(lookup("#rootPane").query());
    }

    private void loadSetupSceneOnFx() {
        interact(() -> {
            try {
                loadSetupScene();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void loadSetupScene() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/openrobotics/fxml/SetupScreen.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root, 1200, 700));
        stage.show();
    }

    private void installRobotPhysicsFields(String battery, String lowBattery, String charge, String energy) {
        interact(() -> {
            setField("batteryCapacityField", new TextField(battery));
            setField("lowBatteryField", new TextField(lowBattery));
            setField("chargePerTickField", new TextField(charge));
            setField("energyPerMoveField", new TextField(energy));
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private File saveEngineConfig(Path path, SimulationEngine engine) throws IOException {
        engine.configSaving(path.toAbsolutePath().toString());
        return path.toFile();
    }

    private SimulationEngine emptyEngine() {
        return new SimulationEngine(new Map(3, 3), new Robot[0], new Dispatcher(), CoordinationPolicy.noOp());
    }

    private long countEntities(Map map, Class<? extends MapEntity> type) {
        return map.getEntities().stream().filter(type::isInstance).count();
    }

    private <T extends MapEntity> T onlyEntity(Map map, Class<T> type) {
        List<T> matches = map.getEntities().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
        assertEquals(1, matches.size());
        return matches.get(0);
    }

    private String entitySignature(Map map) {
        return map.getEntities().stream()
                .map(entity -> entity.getClass().getSimpleName()
                        + ":" + entity.getName()
                        + "@" + entity.getPosition())
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private MouseEvent mouseClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                1,
                1,
                1,
                1,
                MouseButton.PRIMARY,
                1,
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

    @SuppressWarnings("unchecked")
    private ComboBox<String> mapCombo() {
        return (ComboBox<String>) field("mapCombo", ComboBox.class);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> policyCombo() {
        return (ComboBox<String>) field("policyCombo", ComboBox.class);
    }

    private Spinner<Integer> reservationKSpinner() {
        return integerSpinner("reservationKSpinner");
    }

    private Spinner<Integer> canvasWidthSpinner() {
        return integerSpinner("canvasWidthSpinner");
    }

    private Spinner<Integer> canvasHeightSpinner() {
        return integerSpinner("canvasHeightSpinner");
    }

    @SuppressWarnings("unchecked")
    private Spinner<Integer> integerSpinner(String fieldName) {
        return (Spinner<Integer>) field(fieldName, Spinner.class);
    }

    private TextField textField(String fieldName) {
        return field(fieldName, TextField.class);
    }

    private Label statusLabel() {
        return field("statusLabel", Label.class);
    }

    private Object invokeOnFx(String name, Class<?>[] parameterTypes, Object... args) {
        AtomicReference<Object> result = new AtomicReference<>();
        interact(() -> result.set(invokePrivate(name, parameterTypes, args)));
        WaitForAsyncUtils.waitForFxEvents();
        return result.get();
    }

    private Object invokePrivate(String name, Class<?>[] parameterTypes, Object... args) {
        if (!Platform.isFxApplicationThread()) {
            AtomicReference<Object> result = new AtomicReference<>();
            interact(() -> result.set(invokePrivate(name, parameterTypes, args)));
            WaitForAsyncUtils.waitForFxEvents();
            return result.get();
        }
        try {
            Method method = SetupController.class.getDeclaredMethod(name, parameterTypes);
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

    private <T> T field(String name, Class<T> type) {
        try {
            Field field = SetupController.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(controller));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setField(String name, Object value) {
        try {
            Field field = SetupController.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(controller, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static class UnsavableEngine extends SimulationEngine {
        UnsavableEngine() {
            super(new Map(2, 2), new Robot[0], new Dispatcher(), CoordinationPolicy.noOp());
        }

        @Override
        public void configSaving(String path) throws IOException {
            throw new IOException("cannot save");
        }
    }
}
