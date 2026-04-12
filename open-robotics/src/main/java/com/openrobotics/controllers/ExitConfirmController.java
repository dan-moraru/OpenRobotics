package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.stage.Stage;

/**
 * Controller for {@code ExitConfirmDialog.fxml}.
 *
 * <p>Implements both {@link ScreenNavigator.DialogController} (to receive the
 * owning stage) and {@link ScreenNavigator.ExitConfirmResultHolder} (so the
 * navigator can read back whether the user confirmed the exit).
 */
public class ExitConfirmController
        implements ScreenNavigator.DialogController,
                   ScreenNavigator.ExitConfirmResultHolder {

    private Stage   dialogStage;
    private boolean confirmed = false;

    // ------------------------------------------------------------------ //
    //  DialogController
    // ------------------------------------------------------------------ //

    @Override
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    // ------------------------------------------------------------------ //
    //  ExitConfirmResultHolder
    // ------------------------------------------------------------------ //

    @Override
    public boolean isConfirmed() {
        return confirmed;
    }

    // ------------------------------------------------------------------ //
    //  FXML initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Nothing to initialise – labels are static in the FXML.
    }

    // ------------------------------------------------------------------ //
    //  Event Handlers
    // ------------------------------------------------------------------ //

    /** User pressed "Yes – Exit": set confirmed flag and close. */
    @FXML
    private void onYes() {
        confirmed = true;
        close();
    }

    /** User pressed "No – Keep Working": leave confirmed = false and close. */
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

    // ------------------------------------------------------------------ //
    //  Helper
    // ------------------------------------------------------------------ //

    private void close() {
        if (dialogStage != null) dialogStage.close();
    }
}

