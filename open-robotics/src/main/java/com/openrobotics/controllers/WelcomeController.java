package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

import java.awt.Desktop;
import java.util.logging.Logger;

/** Controller for WelcomeScreen.fxml; shows branding and changelog, and routes {@code START SETUP} to the setup screen. */
public class WelcomeController {
    private static final Logger LOGGER = Logger.getLogger(WelcomeController.class.getName());

    @FXML
    private StackPane rootPane; // injected but not referenced in code — candidate for removal

    @FXML
    private void initialize() {
        LOGGER.fine("Default JavaFX font: " + javafx.scene.text.Font.getDefault());
    }

    // navigates to the setup screen (§2.2.6)
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
        } catch (Exception ignored) {
        }
    }
}
