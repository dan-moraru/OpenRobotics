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
 * Controller for {@code LoadConfigDialog.fxml}.
 *
 * <p>Implements {@link ScreenNavigator.DialogController} so the navigator
 * can inject the owning dialog stage for self-close behaviour.
 */
public class LoadConfigController implements ScreenNavigator.DialogController {

    @FXML private ComboBox<String> configFileCombo;
    @FXML private Label            selectedPathLabel;

    private Stage dialogStage;
    private File  selectedFile;

    /** Key used to persist recent paths in Java Preferences. */
    private static final String PREFS_KEY = "recentConfigPaths";
    private static final int    MAX_RECENT = 8;

    // ------------------------------------------------------------------ //
    //  DialogController
    // ------------------------------------------------------------------ //

    @Override
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        configFileCombo.getItems().addAll(loadRecentPaths());
        configFileCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                selectedFile = new File(n);
                selectedPathLabel.setText(n);
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Event Handlers
    // ------------------------------------------------------------------ //

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
            }
            configFileCombo.getSelectionModel().select(result.getAbsolutePath());
        }
    }

    @FXML
    private void onLoad() {
        if (selectedFile != null) {
            saveRecentPath(selectedFile.getAbsolutePath());
            // TODO Sprint 4: parse selectedFile and return parsed RunConfig to SetupController
        }
        close();
    }

    @FXML
    private void onCancel() {
        selectedFile = null;
        close();
    }

    // ------------------------------------------------------------------ //
    //  Result accessor (for callers that stored the FXMLLoader)
    // ------------------------------------------------------------------ //

    /** Returns the file chosen by the user, or {@code null} if cancelled. */
    public File getSelectedFile() {
        return selectedFile;
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private void close() {
        if (dialogStage != null) dialogStage.close();
    }

    @SuppressWarnings("unchecked")
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
        for (int i = 0; i < Math.min(current.size(), MAX_RECENT); i++) {
            prefs.put(PREFS_KEY + i, current.get(i));
        }
    }
}

