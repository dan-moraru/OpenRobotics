package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Controller for LoadConfigDialog.fxml; lets the user pick a JSON config file from recent history
 * or the filesystem.
 */
public class LoadConfigController implements ScreenNavigator.DialogController {

    @FXML private ComboBox<String> configFileCombo;
    @FXML private Label            selectedPathLabel;

    private Stage dialogStage;
    private File  selectedFile;

    // key used to persist recent paths in java preferences
    private static final String PREFS_KEY = "recentConfigPaths";
    private static final int    MAX_RECENT = 8;
    private static final String BROWSE_SENTINEL = "Browse...";

    @Override
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    @FXML
    private void initialize() {
        configFileCombo.valueProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            if (BROWSE_SENTINEL.equals(n)) {
                javafx.application.Platform.runLater(() -> {
                    configFileCombo.getSelectionModel().select(o);
                    onBrowse();
                });
                return;
            }
            selectedFile = new File(n);
            selectedPathLabel.setText(n);
        });
        configFileCombo.getItems().addAll(loadRecentPaths());
        configFileCombo.getItems().add(BROWSE_SENTINEL);
    }

    private void ensureBrowseSentinelLast() {
        configFileCombo.getItems().remove(BROWSE_SENTINEL);
        configFileCombo.getItems().add(BROWSE_SENTINEL);
    }

    @FXML
    private void onResetToDefault() {
        selectedFile = null;
        selectedPathLabel.setText("Default configuration will be used.");
        configFileCombo.getSelectionModel().clearSelection();
    }

    @FXML
    private void onBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Configuration File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Config files (*.json, *.txt)", "*.json", "*.txt"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File result = chooser.showOpenDialog(dialogStage);
        if (result != null) {
            selectedFile = result;
            selectedPathLabel.setText(result.getAbsolutePath());
            if (!configFileCombo.getItems().contains(result.getAbsolutePath())) {
                configFileCombo.getItems().add(0, result.getAbsolutePath());
                while (configFileCombo.getItems().size() > MAX_RECENT + 1) {
                    configFileCombo.getItems().remove(configFileCombo.getItems().size() - 2);
                }
            }
            ensureBrowseSentinelLast();
            configFileCombo.getSelectionModel().select(result.getAbsolutePath());
        }
    }

    @FXML
    private void onLoad() {
        if (selectedFile != null) {
            saveRecentPath(selectedFile.getAbsolutePath());
        }
        close();
    }

    @FXML
    private void onCancel() {
        selectedFile = null;
        close();
    }

    /**
     * Returns the file chosen by the user, or {@code null} if the dialog was cancelled.
     * Callers retrieve this after the dialog closes via the FXMLLoader from
     * {@link ScreenNavigator#openDialog}.
     *
     * @return the selected file, or {@code null} if cancelled.
     */
    public File getSelectedFile() {
        return selectedFile;
    }

    private void close() {
        if (dialogStage != null) dialogStage.close();
    }

    private List<String> loadRecentPaths() {
        Preferences prefs = Preferences.userNodeForPackage(LoadConfigController.class);
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String p = prefs.get(PREFS_KEY + i, null);
            if (p != null) paths.add(p);
        }
        return paths;
    }

    private void saveRecentPath(String path) {
        Preferences prefs = Preferences.userNodeForPackage(LoadConfigController.class);
        List<String> current = loadRecentPaths();
        current.remove(path);
        current.add(0, path);
        int writeCount = Math.min(current.size(), MAX_RECENT);
        for (int i = 0; i < writeCount; i++) {
            prefs.put(PREFS_KEY + i, current.get(i));
        }
        for (int i = writeCount; i < MAX_RECENT; i++) {
            prefs.remove(PREFS_KEY + i);
        }
    }
}
