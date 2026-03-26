package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.util.ScreenNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;

/**
 * Controller for {@code SetupScreen.fxml}.
 *
 * <p>Manages all simulation configuration inputs (§4.1.2):
 * <ul>
 *   <li>Map selection / random map generation</li>
 *   <li>Robot count + navigation algorithm</li>
 *   <li>Coordination policy + reservation k</li>
 *   <li>Workload parameters</li>
 *   <li>Simulation tick settings</li>
 *   <li>Load / Save configuration file</li>
 *   <li>Start Simulation</li>
 * </ul>
 */
public class SetupController {

    // ── MAP ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> mapCombo;
    @FXML private CheckBox         randomMapCheck;
    @FXML private TextField        randomSeedField;

    // ── ROBOTS ──────────────────────────────────────────────────────────
    @FXML private Spinner<Integer> robotCountSpinner;
    @FXML private ComboBox<String> navAlgoCombo;

    // ── COORDINATION POLICY ─────────────────────────────────────────────
    @FXML private ComboBox<String> policyCombo;
    @FXML private Spinner<Integer> reservationKSpinner;

    // ── WORKLOAD ────────────────────────────────────────────────────────
    @FXML private ComboBox<String> workloadModeCombo;
    @FXML private TextField        spawnRateField;
    @FXML private TextField        maxTasksField;
    @FXML private TextField        workloadSeedField;

    // ── SIMULATION ──────────────────────────────────────────────────────
    @FXML private TextField maxTicksField;
    @FXML private TextField tickMsField;

    // ── RUN META ────────────────────────────────────────────────────────
    @FXML private TextField runNameField;

    // ── CANVAS ──────────────────────────────────────────────────────────
    @FXML private Spinner<Integer> canvasWidthSpinner;
    @FXML private Spinner<Integer> canvasHeightSpinner;

    // ── STATUS ──────────────────────────────────────────────────────────
    @FXML private Label statusLabel;

    // ------------------------------------------------------------------ //
    //  Default values (spec §5.1.3.4)
    // ------------------------------------------------------------------ //

    private static final int    DEFAULT_ROBOT_COUNT  = 4;
    private static final String DEFAULT_NAV_ALGO     = "GREEDY";
    private static final String DEFAULT_POLICY       = "NONE";
    private static final int    DEFAULT_RESERVATION_K = 3;
    private static final String DEFAULT_WORKLOAD_MODE = "SPAWN_RATE";
    private static final String DEFAULT_SPAWN_RATE   = "10";
    private static final String DEFAULT_MAX_TASKS    = "200";
    private static final String DEFAULT_WORKLOAD_SEED = "42";
    private static final String DEFAULT_MAX_TICKS    = "30000";
    private static final String DEFAULT_TICK_MS      = "50";
    private static final String DEFAULT_RUN_NAME     = "experiment_1";
    private static final int    DEFAULT_CANVAS_TILES = 30;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Map dropdown
        mapCombo.setItems(FXCollections.observableArrayList(
                "baseline_small", "narrow_aisles", "many_intersections"));
        mapCombo.getSelectionModel().selectFirst();

        robotCountSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, DEFAULT_ROBOT_COUNT));
        reservationKSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, DEFAULT_RESERVATION_K));

        // Navigation algorithm
        navAlgoCombo.setItems(FXCollections.observableArrayList(
                "GREEDY", "BUG", "RTA_STAR"));
        navAlgoCombo.getSelectionModel().select(DEFAULT_NAV_ALGO);

        // Coordination policy
        policyCombo.setItems(FXCollections.observableArrayList(
                "NONE", "TRAFFIC_RULES", "RESERVATION_K"));
        policyCombo.getSelectionModel().select(DEFAULT_POLICY);

        // Workload mode
        workloadModeCombo.setItems(FXCollections.observableArrayList(
                "SPAWN_RATE", "FIXED_LIST"));
        workloadModeCombo.getSelectionModel().select(DEFAULT_WORKLOAD_MODE);

        // Canvas size (separate width × height)
        canvasWidthSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5000, AppState.getCanvasWidthTiles()));
        canvasHeightSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5000, AppState.getCanvasHeightTiles()));
        canvasWidthSpinner.valueProperty().addListener((obs, o, n) ->
            AppState.setCanvasDimensions(n, AppState.getCanvasHeightTiles()));
        canvasHeightSpinner.valueProperty().addListener((obs, o, n) ->
            AppState.setCanvasDimensions(AppState.getCanvasWidthTiles(), n));

        // Default text fields
        spawnRateField.setText(DEFAULT_SPAWN_RATE);
        maxTasksField.setText(DEFAULT_MAX_TASKS);
        workloadSeedField.setText(DEFAULT_WORKLOAD_SEED);
        maxTicksField.setText(DEFAULT_MAX_TICKS);
        tickMsField.setText(DEFAULT_TICK_MS);
        runNameField.setText(DEFAULT_RUN_NAME);

        // Disable reservation k spinner unless policy is RESERVATION_K
        reservationKSpinner.setDisable(true);
        policyCombo.valueProperty().addListener((obs, oldVal, newVal) ->
                reservationKSpinner.setDisable(!"RESERVATION_K".equals(newVal)));
    }

    // ------------------------------------------------------------------ //
    //  Reset Handlers  (the ↺ buttons next to each field, §4.1.2)
    // ------------------------------------------------------------------ //

    @FXML private void onResetMap()          { mapCombo.getSelectionModel().selectFirst(); }
    @FXML private void onResetRandomMap()   { randomMapCheck.setSelected(false); }
    @FXML private void onResetRandomSeed()  { randomSeedField.setText(""); }
    @FXML private void onResetRobotCount()   { robotCountSpinner.getValueFactory().setValue(DEFAULT_ROBOT_COUNT); }
    @FXML private void onResetNavAlgo()      { navAlgoCombo.getSelectionModel().select(DEFAULT_NAV_ALGO); }
    @FXML private void onResetPolicy()       { policyCombo.getSelectionModel().select(DEFAULT_POLICY); }
    @FXML private void onResetWorkloadMode() { workloadModeCombo.getSelectionModel().select(DEFAULT_WORKLOAD_MODE); }
    @FXML private void onResetSpawnRate()    { spawnRateField.setText(DEFAULT_SPAWN_RATE); }
    @FXML private void onResetMaxTasks()     { maxTasksField.setText(DEFAULT_MAX_TASKS); }
    @FXML private void onResetWorkloadSeed() { workloadSeedField.setText(DEFAULT_WORKLOAD_SEED); }
    @FXML private void onResetMaxTicks()     { maxTicksField.setText(DEFAULT_MAX_TICKS); }
    @FXML private void onResetTickMs()       { tickMsField.setText(DEFAULT_TICK_MS); }
    @FXML private void onResetRunName()      { runNameField.setText(DEFAULT_RUN_NAME); }
    @FXML private void onResetCanvasTiles()  {
        canvasWidthSpinner.getValueFactory().setValue(DEFAULT_CANVAS_TILES);
        canvasHeightSpinner.getValueFactory().setValue(DEFAULT_CANVAS_TILES);
    }

    // ------------------------------------------------------------------ //
    //  Blocked preview click (§4.1.2 – viewport is disabled in setup)
    // ------------------------------------------------------------------ //

    @FXML
    private void onPreviewBlocked(MouseEvent event) {
        statusLabel.setText("Map preview not interactive during setup.");
        event.consume();
    }

    // ------------------------------------------------------------------ //
    //  Config File Actions
    // ------------------------------------------------------------------ //

    private void debugStatus(String message) {
        statusLabel.setText(message);
        System.out.println("[SetupController] " + message);
    }

    private File getFallbackTestConfig() {
        File fromWorkingDir = new File(System.getProperty("user.dir"), "test_scenario.json");
        if (fromWorkingDir.isFile()) return fromWorkingDir;

        File fromParentDir = new File(System.getProperty("user.dir"), ".." + File.separator + "test_scenario.json");
        if (fromParentDir.isFile()) return fromParentDir;

        return null;
    }

    private boolean promptAndLoadConfig() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Simulation Configuration");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON Config (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File fallback = getFallbackTestConfig();
        debugStatus("Opening config chooser...");
        if (AppState.hasConfigPath()) {
            File current = new File(AppState.getConfigPath());
            File parent = current.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setInitialDirectory(parent);
            }
        } else if (fallback != null) {
            File parent = fallback.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setInitialDirectory(parent);
            }
            chooser.setInitialFileName(fallback.getName());
        }

        Window owner = ScreenNavigator.getPrimaryStage() != null
                ? ScreenNavigator.getPrimaryStage()
                : statusLabel.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            if (fallback != null) {
                debugStatus("Chooser returned no file; using fallback test_scenario.json.");
                return loadConfigFile(fallback);
            }
            debugStatus("Load cancelled (chooser returned no file).");
            return false;
        }
        return loadConfigFile(file);
    }

    private boolean loadConfigFile(File file) {
        try {
            if (file == null || !file.isFile()) {
                debugStatus("\u26a0 Selected file is not accessible: " + (file == null ? "null" : file.getAbsolutePath()));
                return false;
            }
            debugStatus("Loading config: " + file.getAbsolutePath());
            SimulationEngine engine = new SimulationEngine(file.getAbsolutePath());
            if (engine.getMap() == null) {
                String error = engine.getInitError();
                debugStatus("\u26a0 Parse failed: " + (error != null ? error : "unknown error"));
                return false;
            }
            AppState.clear();
            AppState.setConfigPath(file.getAbsolutePath());
            AppState.setEngine(engine);
            debugStatus("\u2714 Loaded: " + file.getName()
                    + "  (" + engine.getMap().getEntities().size() + " entities)");
            return true;
        } catch (Exception e) {
            debugStatus("\u26a0 Error loading file: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
            return false;
        }
    }

    private boolean goToSimulationScreen() {
        try {
            debugStatus("Opening editor screen...");
            ScreenNavigator.goToSimulation();
            return true;
        } catch (Exception e) {
            debugStatus("\u26a0 Could not open editor: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
            e.printStackTrace();
            return false;
        }
    }

    /** Opens a file chooser to pick a JSON config and initialises the engine from it. */
    @FXML
    private void onLoadConfig() {
        if (promptAndLoadConfig()) {
            goToSimulationScreen();
        }
    }

    /** Opens a file chooser to pick a save destination and writes the current engine state. */
    @FXML
    private void onSaveConfig() {
        if (!AppState.hasEngine()) {
            debugStatus("\u26a0 Load a configuration first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Simulation Configuration");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Config (*.json)", "*.json"));
        chooser.setInitialFileName("config_saved.json");
        File file = chooser.showSaveDialog(statusLabel.getScene().getWindow());
        if (file == null) {
            debugStatus("Save cancelled.");
            return;
        }
        try {
            AppState.getEngine().configSaving(file.getAbsolutePath());
            debugStatus("\u2714 Saved: " + file.getName());
        } catch (Exception e) {
            debugStatus("\u26a0 Save error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Start Simulation
    // ------------------------------------------------------------------ //

    @FXML
    private void onStartSimulation() {
        debugStatus("Start Simulation clicked.");
        if (!validate()) return;
        if (!AppState.hasEngine() && !AppState.hasConfigPath()) {
            File fallback = getFallbackTestConfig();
            if (fallback != null) {
                debugStatus("No loaded config in memory; using fallback test config.");
                if (!loadConfigFile(fallback)) {
                    return;
                }
            } else if (!promptAndLoadConfig()) {
                return;
            }
        }
        if (!AppState.hasEngine() && AppState.hasConfigPath()) {
            debugStatus("Rebuilding engine from saved config path.");
            SimulationEngine engine;
            try {
                engine = new SimulationEngine(AppState.getConfigPath());
            } catch (Exception e) {
                debugStatus("\u26a0 Config reload failed: " + e.getMessage());
                return;
            }
            if (engine.getMap() == null) {
                String error = engine.getInitError();
                debugStatus("\u26a0 Config reload failed: " + (error != null ? error : "unknown error"));
                return;
            }
            AppState.setEngine(engine);
        }
        if (!AppState.hasEngine()) {
            debugStatus("\u26a0 Please load a configuration file first.");
            return;
        }
        goToSimulationScreen();
    }

    /** Basic validation – returns {@code true} if all required fields are filled. */
    private boolean validate() {
        if (mapCombo.getValue() == null) {
            statusLabel.setText("⚠ Please select a map.");
            return false;
        }
        try {
            int ticks = Integer.parseInt(maxTicksField.getText().trim());
            if (ticks <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠ Max ticks must be a positive integer.");
            return false;
        }
        try {
            int tickMs = Integer.parseInt(tickMsField.getText().trim());
            if (tickMs <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠ Tick ms must be a positive integer.");
            return false;
        }
        statusLabel.setText("");
        return true;
    }

    // ------------------------------------------------------------------ //
    //  Reset for reservation k
    // ------------------------------------------------------------------ //

    @FXML
    private void onResetReservationK() {
        reservationKSpinner.getValueFactory().setValue(DEFAULT_RESERVATION_K);
    }

    // ------------------------------------------------------------------ //
    //  Tab strip
    // ------------------------------------------------------------------ //

    @FXML
    private void onTabEditor() { /* already on setup/editor screen */ }

    // ------------------------------------------------------------------ //
    //  Menu items
    // ------------------------------------------------------------------ //

    @FXML
    private void onMenuWelcome() { ScreenNavigator.goToWelcome(); }

    @FXML
    private void onMenuGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com"));
        } catch (Exception ex) {
            statusLabel.setText("Could not open browser.");
        }
    }

    // ------------------------------------------------------------------ //
    //  Global Exit
    // ------------------------------------------------------------------ //

    @FXML
    private void onExit() {
        if (ScreenNavigator.confirmExit()) {
            javafx.application.Platform.exit();
        }
    }
}
