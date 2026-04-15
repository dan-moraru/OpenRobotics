package com.openrobotics.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openrobotics.AppState;
import com.openrobotics.io.ResultsExportDTO;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Controller for ExportResultsDialog.fxml; serializes post-simulation results to a user-chosen
 * JSON file.
 */
public class ExportResultsController implements ScreenNavigator.DialogController {

    @FXML private TextField        fileNameField;
    @FXML private ComboBox<String> directoryCombo;
    @FXML private Label            selectedDirLabel;

    private Stage dialogStage;
    private File  selectedDirectory;

    private static final String PREFS_KEY   = "recentExportDirs";
    private static final int    MAX_RECENT  = 8;
    private static final String DEFAULT_DIR =
            System.getProperty("user.home") + File.separator + "Documents";

    @Override
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    @FXML
    private void initialize() {
        directoryCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                selectedDirectory = new File(n);
                selectedDirLabel.setText(n);
            }
        });

        directoryCombo.getItems().addAll(loadRecentDirs());
        if (!directoryCombo.getItems().isEmpty()) {
            directoryCombo.getSelectionModel().selectFirst();
        }

        fileNameField.setText("results_" +
                java.time.LocalDate.now().toString() + ".json");
    }

    @FXML
    private void onResetDirectory() {
        selectedDirectory = new File(DEFAULT_DIR);
        selectedDirLabel.setText(DEFAULT_DIR);
        if (!directoryCombo.getItems().contains(DEFAULT_DIR)) {
            directoryCombo.getItems().add(0, DEFAULT_DIR);
        }
        directoryCombo.getSelectionModel().select(DEFAULT_DIR);
    }

    @FXML
    private void onBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Export Directory");
        File result = chooser.showDialog(dialogStage);
        if (result != null) {
            selectedDirectory = result;
            selectedDirLabel.setText(result.getAbsolutePath());
            if (!directoryCombo.getItems().contains(result.getAbsolutePath())) {
                directoryCombo.getItems().add(0, result.getAbsolutePath());
            }
            directoryCombo.getSelectionModel().select(result.getAbsolutePath());
        }
    }

    @FXML
    private void onExport() {
        if (selectedDirectory == null) {
            selectedDirLabel.setText("Select a directory before exporting.");
            return;
        }
        String fileName = fileNameField.getText() == null ? "" : fileNameField.getText().trim();
        if (fileName.isEmpty()) {
            selectedDirLabel.setText("File name cannot be empty.");
            return;
        }
        if (!fileName.matches("^[^\\\\/:\\*?\"<>|\\p{Cntrl}]+$")) {
            selectedDirLabel.setText("File name contains invalid characters.");
            return;
        }
        if (!selectedDirectory.exists()) {
            selectedDirLabel.setText("Selected directory does not exist.");
            return;
        }
        if (!selectedDirectory.canWrite()) {
            selectedDirLabel.setText("Selected directory is not writable.");
            return;
        }

        SimulationEngine engine = AppState.getEngine();
        if (engine == null) {
            selectedDirLabel.setText("No simulation data available to export.");
            return;
        }

        try {
            ResultsExportDTO dto = buildDTO(engine);
            String baseName = fileName.endsWith(".json") ? fileName.replace(".json", "") : fileName;
            String fullName = baseName + ".json";
            File outputFile = new File(selectedDirectory, fullName);

            if (outputFile.exists()) {
                int counter = 1;
                while (outputFile.exists()) {
                    outputFile = new File(selectedDirectory, baseName + "_" + counter + ".json");
                    counter++;
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(outputFile, dto);

            saveRecentDir(selectedDirectory.getAbsolutePath());
            close();
        } catch (Exception e) {
            selectedDirLabel.setText("Export failed: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    // builds the export DTO from current engine state
    private ResultsExportDTO buildDTO(SimulationEngine engine) {
        ResultsExportDTO dto = new ResultsExportDTO();
        dto.runName    = AppState.getConfigPath() != null ? AppState.getConfigPath() : "unknown";
        dto.totalTicks = engine.getTickCounter();

        Robot[] robots = engine.getRobots();
        int sumTasks = 0;

        dto.robots = new ArrayList<>();
        if (robots != null) {
            for (Robot r : robots) {
                ResultsExportDTO.RobotResultDTO rDto = new ResultsExportDTO.RobotResultDTO();
                rDto.name                = r.getName();
                rDto.navigationAlgorithm = r.getNav() != null
                        ? r.getNav().getClass().getSimpleName().replace("NavigationStrategy", "")
                        : "None";
                rDto.tasksCompleted      = r.getTasksCompleted();
                rDto.totalDistanceMoved  = r.getTotalDistanceMoved();
                rDto.totalEnergyConsumed = r.getTotalEnergyConsumed();
                rDto.totalIdleTicks      = r.getTotalIdleTicks();
                rDto.stuckTicks          = r.getStuckTicks();
                rDto.battery             = r.getBattery();
                rDto.finalState          = r.getState().name();
                dto.robots.add(rDto);
                sumTasks += r.getTasksCompleted();
            }
        }

        dto.completedTasks = sumTasks;
        dto.pendingTasks   = engine.getDispatcher() != null
                ? engine.getDispatcher().getAllQueuedTasks().size() : 0;
        dto.totalTasks     = Math.max(dto.completedTasks + dto.pendingTasks,
                engine.getDispatcher() != null
                        ? engine.getDispatcher().getTotalTasksAdded() : 0);
        dto.throughput     = dto.totalTicks > 0
                ? (double) dto.completedTasks / dto.totalTicks : 0;
        dto.completionRate = dto.totalTasks > 0
                ? 100.0 * dto.completedTasks / dto.totalTasks : 0;

        return dto;
    }

    private void close() {
        if (dialogStage != null) dialogStage.close();
    }

    private List<String> loadRecentDirs() {
        Preferences prefs = Preferences.userNodeForPackage(ExportResultsController.class);
        List<String> dirs = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String d = prefs.get(PREFS_KEY + i, null);
            if (d != null) dirs.add(d);
        }
        return dirs;
    }

    private void saveRecentDir(String dir) {
        Preferences prefs = Preferences.userNodeForPackage(ExportResultsController.class);
        List<String> current = loadRecentDirs();
        current.remove(dir);
        current.add(0, dir);
        for (int i = 0; i < Math.min(current.size(), MAX_RECENT); i++) {
            prefs.put(PREFS_KEY + i, current.get(i));
        }
    }
}
