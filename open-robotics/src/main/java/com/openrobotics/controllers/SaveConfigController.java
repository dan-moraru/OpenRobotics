package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Controller for {@code SaveConfigDialog.fxml}.
 *
 * <p>Implements {@link ScreenNavigator.DialogController} so the navigator
 * can inject the owning dialog stage for self-close behaviour.
 */
public class SaveConfigController implements ScreenNavigator.DialogController {

    @FXML private TextField        fileNameField;
    @FXML private ComboBox<String> directoryCombo;
    @FXML private Label            selectedDirLabel;

    private Stage  dialogStage;
    private File   selectedDirectory;
    private String resultFilePath; // set in onSave(), read by caller after dialog closes

    private static final String PREFS_KEY  = "recentSaveDirs";
    private static final int    MAX_RECENT = 8;
    private static final String DEFAULT_DIR = System.getProperty("user.home") + File.separator + ".open-robotics" + File.separator + "configs";
    private static final String BROWSE_SENTINEL = "Browse...";
    private static final String CONFIG_DIR = "./configs";

    // ------------------------------------------------------------------ //
    //  DialogController
    // ------------------------------------------------------------------ //

    @Override
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /** Returns the full path (directory + file name) set when the user clicked Save, or null if not saved yet. */
    public String getResultFilePath() {
        return resultFilePath;
    }

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        directoryCombo.valueProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            if (BROWSE_SENTINEL.equals(n)) {
                // Defer so the popup closes before opening the native chooser,
                // then restore the prior selection (or clear it) before browsing.
                javafx.application.Platform.runLater(() -> {
                    directoryCombo.getSelectionModel().select(o);
                    onBrowse();
                });
                return;
            }
            selectedDirectory = new File(n);
            selectedDirLabel.setText(n);
        });

        directoryCombo.getItems().addAll(loadRecentDirs());
        directoryCombo.getItems().add(BROWSE_SENTINEL);
        if (directoryCombo.getItems().size() > 1) {
            directoryCombo.getSelectionModel().selectFirst();
            selectedDirLabel.setText(directoryCombo.getSelectionModel().getSelectedItem());
        } else if (selectedDirLabel != null) {
            selectedDirLabel.setText("");
        }

        fileNameField.setText("experiment_" +
                java.time.LocalDate.now().toString() + ".json");
    }

    // ------------------------------------------------------------------ //
    //  Event Handlers
    // ------------------------------------------------------------------ //

    @FXML
    private void onResetDirectory() {
        selectedDirectory = new File(DEFAULT_DIR);
        selectedDirLabel.setText(DEFAULT_DIR);
        if (!directoryCombo.getItems().contains(DEFAULT_DIR)) {
            directoryCombo.getItems().add(0, DEFAULT_DIR);
        }
        ensureBrowseSentinelLast();
        directoryCombo.getSelectionModel().select(DEFAULT_DIR);
    }

    @FXML
    private void onBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Save Directory");
        File result = chooser.showDialog(dialogStage);
        if (result != null) {
            selectedDirectory = result;
            selectedDirLabel.setText(result.getAbsolutePath());
            if (!directoryCombo.getItems().contains(result.getAbsolutePath())) {
                directoryCombo.getItems().add(0, result.getAbsolutePath());
            }
            ensureBrowseSentinelLast();
            directoryCombo.getSelectionModel().select(result.getAbsolutePath());
        }
    }

    private void ensureBrowseSentinelLast() {
        directoryCombo.getItems().remove(BROWSE_SENTINEL);
        directoryCombo.getItems().add(BROWSE_SENTINEL);
    }

    @FXML
    private void onSave() {
        if (selectedDirectory == null) {
            selectedDirLabel.setText("Select a directory before saving.");
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
        if (!selectedDirectory.isDirectory()) {
            selectedDirLabel.setText("Selected path is not a directory.");
            return;
        }
        if (!selectedDirectory.canWrite()) {
            selectedDirLabel.setText("Selected directory is not writable.");
            return;
        }

        String fullPath = new File(selectedDirectory, fileName).getAbsolutePath();
        SimulationEngine engine = AppState.getEngine();
        if (engine == null) {
            selectedDirLabel.setText("No simulation engine available.");
            return;
        }
        try {
            engine.configSaving(fullPath);
            saveRecentDir(selectedDirectory.getAbsolutePath());
            resultFilePath = fullPath;

            // Automatically save a copy to the configs directory for the list
            File configsDir = new File(CONFIG_DIR);
            if (!configsDir.exists()) {
                configsDir.mkdirs();
            }
            File configsFile = new File(configsDir, fileName);
            File sourceFile = new File(fullPath);
            if (sourceFile.exists() && !sourceFile.equals(configsFile)) {
                Files.copy(sourceFile.toPath(), configsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            close();
        } catch (Exception ex) {
            selectedDirLabel.setText("Save failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        selectedDirectory = null;
        close();
    }

    // ------------------------------------------------------------------ //
    //  Result accessors
    // ------------------------------------------------------------------ //

    public File getSelectedDirectory() { return selectedDirectory; }
    public String getFileName() {
        String value = fileNameField == null ? null : fileNameField.getText();
        return value == null ? "" : value.trim();
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private void close() {
        if (dialogStage != null) dialogStage.close();
    }

    private List<String> loadRecentDirs() {
        Preferences prefs = Preferences.userNodeForPackage(SaveConfigController.class);
        List<String> dirs = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String d = prefs.get(PREFS_KEY + i, null);
            if (d != null) dirs.add(d);
        }
        return dirs;
    }

    private void saveRecentDir(String dir) {
        Preferences prefs = Preferences.userNodeForPackage(SaveConfigController.class);
        List<String> current = loadRecentDirs();
        current.remove(dir);
        current.add(0, dir);
        int writeCount = Math.min(current.size(), MAX_RECENT);
        for (int i = 0; i < writeCount; i++) {
            prefs.put(PREFS_KEY + i, current.get(i));
        }
        for (int i = writeCount; i < MAX_RECENT; i++) {
            prefs.remove(PREFS_KEY + i);
        }
    }
}

