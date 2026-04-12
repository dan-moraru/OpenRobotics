package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.util.logging.Logger;

/**
 * Controller for {@code WelcomeScreen.fxml}.
 *
 * <p>The welcome screen (§4.1.1) shows the application logo, branding, and
 * a changelog panel. Pressing START SETUP navigates to the Setup screen.
 */
public class WelcomeController {
    private static final Logger LOGGER = Logger.getLogger(WelcomeController.class.getName());

    @FXML private StackPane rootPane;
    @FXML private VBox      changelogContent;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        LOGGER.fine("Default JavaFX font: " + javafx.scene.text.Font.getDefault());
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

    @FXML
    private void onExit() {
        Platform.exit();
    }

    @FXML
    private void onMenuGithub() {
        try {
            Desktop.getDesktop().browse(new java.net.URI("https://github.com/dan-moraru/OpenRobotics"));
        } catch (Exception ignored) {}
    }
}
