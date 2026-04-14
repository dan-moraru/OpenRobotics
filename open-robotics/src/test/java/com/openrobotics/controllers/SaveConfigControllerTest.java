package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SaveConfigControllerTest extends ApplicationTest {

    private SaveConfigController controller;
    private Stage dialogStage;

    private void clearPrefs() throws Exception {
        Preferences prefs = Preferences.userNodeForPackage(SaveConfigController.class);
        for (int i = 0; i < 8; i++) {
            prefs.remove("recentSaveDirs" + i);
        }
        prefs.flush();
    }

    @Override
    public void start(Stage stage) throws Exception {
        clearPrefs();
        dialogStage = stage;
        ScreenNavigator.setPrimaryStage(stage);
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/openrobotics/fxml/SaveConfigDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        controller.setDialogStage(stage);
        stage.setScene(new Scene(root));
        stage.show();
    }

    @Test
    void initialize_sets_default_filename() {
        TextField fileNameField = lookup("#fileNameField").queryAs(TextField.class);
        assertTrue(fileNameField.getText().endsWith(".json"));
    }

    @Test
    void reset_directory_sets_default_directory() {
        clickOn("↺");
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lookup("#selectedDirLabel").queryAs(Label.class).getText().contains(".open-robotics"));
    }

    @Test
    void save_without_directory_shows_error() {
        clickOn(lookup((Button b) -> "SAVE".equals(b.getText())).queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Select a directory before saving.",
                lookup("#selectedDirLabel").queryAs(Label.class).getText());
    }

    @Test
    void save_with_invalid_filename_shows_error() throws Exception {
        Path dir = Files.createTempDirectory("save-config-test");
        try {
            ComboBox<String> combo = lookup("#directoryCombo").queryAs(ComboBox.class);
            TextField fileNameField = lookup("#fileNameField").queryAs(TextField.class);
            interact(() -> {
                combo.getItems().add(dir.toString());
                combo.getSelectionModel().select(dir.toString());
                fileNameField.setText("bad:name.json");
            });
            WaitForAsyncUtils.waitForFxEvents();

            clickOn(lookup((Button b) -> "SAVE".equals(b.getText())).queryAs(Button.class));
            WaitForAsyncUtils.waitForFxEvents();

            assertEquals("File name contains invalid characters.",
                    lookup("#selectedDirLabel").queryAs(Label.class).getText());
        } finally {
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void save_with_nonexistent_directory_shows_error() {
        File missing = new File(System.getProperty("java.io.tmpdir"), "definitely-missing-openrobotics-save-dir");
        ComboBox<String> combo = lookup("#directoryCombo").queryAs(ComboBox.class);
        interact(() -> {
            combo.getItems().add(missing.getAbsolutePath());
            combo.getSelectionModel().select(missing.getAbsolutePath());
        });
        WaitForAsyncUtils.waitForFxEvents();

        clickOn(lookup((Button b) -> "SAVE".equals(b.getText())).queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Selected directory does not exist.",
                lookup("#selectedDirLabel").queryAs(Label.class).getText());
    }

    @Test
    void save_with_file_path_instead_of_directory_shows_error() throws Exception {
        Path file = Files.createTempFile("save-config-test", ".json");
        try {
            ComboBox<String> combo = lookup("#directoryCombo").queryAs(ComboBox.class);
            interact(() -> {
                combo.getItems().add(file.toString());
                combo.getSelectionModel().select(file.toString());
            });
            WaitForAsyncUtils.waitForFxEvents();

            clickOn(lookup((Button b) -> "SAVE".equals(b.getText())).queryAs(Button.class));
            WaitForAsyncUtils.waitForFxEvents();

            assertEquals("Selected path is not a directory.",
                    lookup("#selectedDirLabel").queryAs(Label.class).getText());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void successful_save_validation_closes_dialog_and_preserves_selection() throws Exception {
        Path dir = Files.createTempDirectory("save-config-test");
        try {
            ComboBox<String> combo = lookup("#directoryCombo").queryAs(ComboBox.class);
            TextField fileNameField = lookup("#fileNameField").queryAs(TextField.class);
            interact(() -> {
                combo.getItems().add(dir.toString());
                combo.getSelectionModel().select(dir.toString());
                fileNameField.setText("..valid.json");
            });
            WaitForAsyncUtils.waitForFxEvents();

            clickOn(lookup((Button b) -> "SAVE".equals(b.getText())).queryAs(Button.class));
            WaitForAsyncUtils.waitForFxEvents();

            assertEquals(dir.toFile(), controller.getSelectedDirectory());
            assertEquals("..valid.json", controller.getFileName());
            assertFalse(dialogStage.isShowing());
        } finally {
            try (var entries = Files.list(dir)) {
                entries.forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            } catch (Exception ignored) {}
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void cancel_clears_selected_directory_and_closes_dialog() {
        clickOn(lookup((Button b) -> "CANCEL".equals(b.getText())).queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertNull(controller.getSelectedDirectory());
        assertFalse(dialogStage.isShowing());
    }
}
