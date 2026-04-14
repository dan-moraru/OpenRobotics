package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadConfigControllerTest extends ApplicationTest {

    private LoadConfigController controller;
    private Stage dialogStage;

    private void clearPrefs() throws Exception {
        Preferences prefs = Preferences.userNodeForPackage(LoadConfigController.class);
        for (int i = 0; i < 8; i++) {
            prefs.remove("recentConfigPaths" + i);
        }
        prefs.flush();
    }

    @Override
    public void start(Stage stage) throws Exception {
        clearPrefs();
        dialogStage = stage;
        ScreenNavigator.setPrimaryStage(stage);
        URL fxml = Objects.requireNonNull(
                getClass().getResource("/com/openrobotics/fxml/LoadConfigDialog.fxml"),
                "LoadConfigDialog.fxml");
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(fxml);
        loader.setClassLoader(getClass().getClassLoader());
        try (InputStream in = fxml.openStream()) {
            Parent root = loader.load(in);
            controller = loader.getController();
            controller.setDialogStage(stage);
            stage.setScene(new Scene(root));
            stage.show();
        }
    }

    @Test
    void selecting_combo_value_updates_selected_file_and_label() {
        String path = new File("sample-config.json").getAbsolutePath();
        ComboBox<String> combo = lookup("#configFileCombo").queryAs(ComboBox.class);

        interact(() -> {
            combo.getItems().add(path);
            combo.getSelectionModel().select(path);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(path, lookup("#selectedPathLabel").queryAs(Label.class).getText());
        assertEquals(path, controller.getSelectedFile().getAbsolutePath());
    }

    @Test
    void reset_to_default_clears_selection() {
        String path = new File("sample-config.json").getAbsolutePath();
        ComboBox<String> combo = lookup("#configFileCombo").queryAs(ComboBox.class);
        interact(() -> {
            combo.getItems().add(path);
            combo.getSelectionModel().select(path);
        });
        WaitForAsyncUtils.waitForFxEvents();

        clickOn("↺");
        WaitForAsyncUtils.waitForFxEvents();

        assertNull(controller.getSelectedFile());
        assertEquals("Default configuration will be used.",
                lookup("#selectedPathLabel").queryAs(Label.class).getText());
    }

    @Test
    void cancel_clears_selection_and_closes_dialog() {
        clickOn(lookup((Button b) -> "CANCEL".equals(b.getText())).queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertNull(controller.getSelectedFile());
        assertFalse(dialogStage.isShowing());
    }

    @Test
    void load_closes_dialog_when_file_selected() {
        String path = new File("sample-config.json").getAbsolutePath();
        ComboBox<String> combo = lookup("#configFileCombo").queryAs(ComboBox.class);
        interact(() -> {
            combo.getItems().add(path);
            combo.getSelectionModel().select(path);
        });
        WaitForAsyncUtils.waitForFxEvents();

        clickOn(lookup((Button b) -> "LOAD".equals(b.getText())).queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(controller.getSelectedFile().getAbsolutePath().endsWith("sample-config.json"));
        assertFalse(dialogStage.isShowing());
    }
}
