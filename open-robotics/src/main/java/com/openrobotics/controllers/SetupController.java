package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

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

    // ── STATUS ──────────────────────────────────────────────────────────
    @FXML private Label statusLabel;

    // ------------------------------------------------------------------ //
    //  Default values (spec §5.1.3.4)
    // ------------------------------------------------------------------ //

    private static final int    DEFAULT_ROBOT_COUNT  = 4;
    private static final String DEFAULT_NAV_ALGO     = "GREEDY";
    private static final String DEFAULT_POLICY       = "TRAFFIC_RULES";
    private static final int    DEFAULT_RESERVATION_K = 3;
    private static final String DEFAULT_WORKLOAD_MODE = "SPAWN_RATE";
    private static final String DEFAULT_SPAWN_RATE   = "10";
    private static final String DEFAULT_MAX_TASKS    = "200";
    private static final String DEFAULT_WORKLOAD_SEED = "42";
    private static final String DEFAULT_MAX_TICKS    = "30000";
    private static final String DEFAULT_TICK_MS      = "50";
    private static final String DEFAULT_RUN_NAME     = "experiment_1";

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Map dropdown
        mapCombo.setItems(FXCollections.observableArrayList(
                "baseline_small", "narrow_aisles", "many_intersections"));
        mapCombo.getSelectionModel().selectFirst();

        // Navigation algorithm
        navAlgoCombo.setItems(FXCollections.observableArrayList(
                "GREEDY", "BUG", "RTA_STAR"));
        navAlgoCombo.getSelectionModel().select(DEFAULT_NAV_ALGO);

        // Coordination policy
        policyCombo.setItems(FXCollections.observableArrayList(
                "TRAFFIC_RULES", "RESERVATION_K"));
        policyCombo.getSelectionModel().select(DEFAULT_POLICY);

        // Workload mode
        workloadModeCombo.setItems(FXCollections.observableArrayList(
                "SPAWN_RATE", "FIXED_LIST"));
        workloadModeCombo.getSelectionModel().select(DEFAULT_WORKLOAD_MODE);

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

    /** Opens the Load Config specialty dialog (§4.2). */
    @FXML
    private void onLoadConfig() {
        ScreenNavigator.openDialog(ScreenNavigator.DIALOG_LOAD_CONFIG, "Load Configuration");
        // TODO Sprint 4: read returned config and populate fields
        statusLabel.setText("Configuration loaded.");
    }

    /** Opens the Save Config specialty dialog (§4.2). */
    @FXML
    private void onSaveConfig() {
        ScreenNavigator.openDialog(ScreenNavigator.DIALOG_SAVE_CONFIG, "Save Configuration");
        statusLabel.setText("Configuration saved.");
    }

    // ------------------------------------------------------------------ //
    //  Start Simulation
    // ------------------------------------------------------------------ //

    @FXML
    private void onStartSimulation() {
        if (!validate()) return;
        // TODO Sprint 4: build RunConfig object from fields and pass to engine
        ScreenNavigator.goToSimulation();
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

