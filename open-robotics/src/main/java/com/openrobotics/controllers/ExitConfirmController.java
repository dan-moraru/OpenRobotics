package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.stage.Stage;

/**
 * Controller for ExitConfirmDialog.fxml; implements both {@link ScreenNavigator.DialogController}
 * and {@link ScreenNavigator.ExitConfirmResultHolder}.
 */
public class ExitConfirmController
        implements ScreenNavigator.DialogController,
                   ScreenNavigator.ExitConfirmResultHolder {

    private Stage   dialogStage;
    private boolean confirmed = false;

    @Override
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    @Override
    public boolean isConfirmed() {
        return confirmed;
    }

    @FXML
    private void onYes() {
        confirmed = true;
        close();
    }

    @FXML
    private void onNo() {
        confirmed = false;
        close();
    }

    @FXML
    private void onQuit() {
        onYes();
    }

    @FXML
    private void onReturn() {
        onNo();
    }

    private void close() {
        if (dialogStage != null) dialogStage.close();
    }
}
