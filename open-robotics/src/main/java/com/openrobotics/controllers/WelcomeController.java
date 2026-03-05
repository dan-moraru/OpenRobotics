package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller for {@code WelcomeScreen.fxml}.
 *
 * <p>The welcome screen (§4.1.1) shows the application logo, branding, and
 * a changelog panel. Clicking anywhere on the screen navigates to the Setup
 * screen.
 */
public class WelcomeController {

    @FXML private StackPane rootPane;
    @FXML private VBox      changelogContent;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // TODO Sprint 6: fetch real changelog / version history from DB or
        //  bundled resource file and populate changelogContent dynamically.
    }

    // ------------------------------------------------------------------ //
    //  Event Handlers
    // ------------------------------------------------------------------ //

    /** Any click on the root pane navigates to the Setup screen (§2.2.6). */
    @FXML
    private void onAnyClick(MouseEvent event) {
        ScreenNavigator.goToSetup();
    }
}

