package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExitConfirmControllerTest extends ApplicationTest {

    private ExitConfirmController controller;
    private Stage dialogStage;

    @Override
    public void start(Stage stage) throws Exception {
        dialogStage = stage;
        ScreenNavigator.setPrimaryStage(stage);
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/openrobotics/fxml/ExitConfirmDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        controller.setDialogStage(stage);
        stage.setScene(new Scene(root));
        stage.show();
    }

    @Test
    void yes_confirms_and_closes_dialog() {
        clickOn(lookup((Button b) -> "QUIT".equals(b.getText())).queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(controller.isConfirmed());
        assertFalse(dialogStage.isShowing());
    }

    @Test
    void no_keeps_unconfirmed_and_closes_dialog() {
        clickOn(lookup((Button b) -> "RETURN".equals(b.getText())).queryAs(Button.class));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(controller.isConfirmed());
        assertFalse(dialogStage.isShowing());
    }
}
