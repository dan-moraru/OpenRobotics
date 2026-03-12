package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller for {@code WelcomeScreen.fxml}.
 *
 * <p>The welcome screen (§4.1.1) shows the application logo, branding, and
 * a changelog panel. Pressing START SETUP navigates to the Setup screen.
 */
public class WelcomeController {

    @FXML private StackPane rootPane;
    @FXML private VBox      changelogContent;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // TEMPORARY DEBUG LINE
        System.out.println(javafx.scene.text.Font.getDefault());
        // TODO Sprint 6: fetch real changelog / version history from DB or
        //  bundled resource file and populate changelogContent dynamically.
    }

    // ------------------------------------------------------------------ //
    //  Event Handlers
    // ------------------------------------------------------------------ //

    /** START SETUP button navigates to the Setup screen (§2.2.6). */
    @FXML
    private void onStartSetup(ActionEvent event) {
        ScreenNavigator.goToSetup();
    }
}
