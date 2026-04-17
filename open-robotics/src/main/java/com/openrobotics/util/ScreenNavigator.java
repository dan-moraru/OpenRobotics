package com.openrobotics.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/** Centralises all screen and dialog transitions; holds the primary stage so controllers do not need to pass it around. */
public final class ScreenNavigator {

    // FXML resource paths (relative to the resources root)
    public static final String WELCOME    = "/com/openrobotics/fxml/WelcomeScreen.fxml";
    public static final String SETUP      = "/com/openrobotics/fxml/SetupScreen.fxml";
    public static final String SIMULATION = "/com/openrobotics/fxml/SimulationScreen.fxml";
    public static final String RESULTS    = "/com/openrobotics/fxml/ResultsScreen.fxml";

    public static final String DIALOG_LOAD_CONFIG   = "/com/openrobotics/fxml/LoadConfigDialog.fxml";
    public static final String DIALOG_SAVE_CONFIG   = "/com/openrobotics/fxml/SaveConfigDialog.fxml";
    public static final String DIALOG_OBJECT_DESC   = "/com/openrobotics/fxml/ObjectDescDialog.fxml";
    public static final String DIALOG_EXIT_CONFIRM  = "/com/openrobotics/fxml/ExitConfirmDialog.fxml";
    public static final String DIALOG_EXPORT_RESULTS = "/com/openrobotics/fxml/ExportResultsDialog.fxml";

    // set once in MainApp.start()
    private static Stage primaryStage;
    // used to call cleanup() before replacing the screen
    private static Object currentController;

    private ScreenNavigator() {}

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static Object getCurrentController() {
        return currentController;
    }

    /**
     * Loads and displays the given FXML as the primary scene.
     *
     * @param fxmlPath one of the path constants in this class
     */
    public static void loadScreen(String fxmlPath) {
        try {
            if (currentController instanceof Cleanable c) {
                c.cleanup();
            }

            URL url = ScreenNavigator.class.getResource(fxmlPath);
            if (url == null) {
                throw new IllegalArgumentException("FXML resource not found: " + fxmlPath);
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            if (primaryStage == null) {
                throw new IllegalStateException("Primary stage not set. Call ScreenNavigator.setPrimaryStage first.");
            }
            currentController = loader.getController();
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            primaryStage.show();
        } catch (Exception e) {
            System.err.println("[ScreenNavigator] Failed to load screen: " + fxmlPath + " -> " + e);
            throw new RuntimeException("Failed to load screen: " + fxmlPath, e);
        }
    }

    /**
     * Opens the given FXML as a blocking modal dialog.
     *
     * @param fxmlPath the path to the dialog FXML
     * @return the loader after the dialog closes, so callers can retrieve controller data
     */
    public static FXMLLoader openDialog(String fxmlPath) {
        return openDialog(fxmlPath, "", null);
    }

    /**
     * Opens the given FXML as a blocking modal dialog with the given title.
     *
     * @param fxmlPath the path to the dialog FXML
     * @param title the window title to display
     * @return the loader after the dialog closes, so callers can retrieve controller data
     */
    public static FXMLLoader openDialog(String fxmlPath, String title) {
        return openDialog(fxmlPath, title, null);
    }

    /**
     * Opens a modal dialog with a custom title and optional controller initializer.
     *
     * @param fxmlPath the path to the dialog FXML
     * @param title the window title; ignored if blank
     * @param controllerInitializer optional callback to configure the controller before the dialog opens
     * @return the loader after the dialog closes, so callers can retrieve controller data
     */
    public static FXMLLoader openDialog(String fxmlPath, String title, Consumer<Object> controllerInitializer) {
        try {
            URL url = ScreenNavigator.class.getResource(fxmlPath);
            if (url == null) {
                throw new IllegalArgumentException("FXML resource not found: " + fxmlPath);
            }
            ResourceBundle bundle = resolveBundleForFxml(fxmlPath);
            FXMLLoader loader = bundle != null ? new FXMLLoader(url, bundle) : new FXMLLoader(url);
            Parent root = loader.load();
            if (primaryStage == null) {
                throw new IllegalStateException("Primary stage not set. Call ScreenNavigator.setPrimaryStage first.");
            }

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            if (title != null && !title.isBlank()) dialogStage.setTitle(title);

            // Pass the stage to the controller so it can close itself
            Object controller = loader.getController();
            if (controller instanceof DialogController dc) {
                dc.setDialogStage(dialogStage);
            }
            if (controllerInitializer != null) {
                controllerInitializer.accept(controller);
            }

            Scene scene = new Scene(root, Color.TRANSPARENT);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

            return loader;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open dialog: " + fxmlPath, e);
        }
    }

    public static void goToWelcome()    { loadScreen(WELCOME); }
    public static void goToSetup()      { loadScreen(SETUP); }
    public static void goToSimulation() { loadScreen(SIMULATION); }
    public static void goToResults()    { loadScreen(RESULTS); }

    /**
     * Shows the exit-confirm dialog and returns whether the user confirmed.
     *
     * @return {@code true} if the user pressed the confirm button
     */
    public static boolean confirmExit() {
        FXMLLoader loader = openDialog(DIALOG_EXIT_CONFIRM, "Exit");
        Object controller = loader.getController();
        if (controller instanceof ExitConfirmResultHolder holder) {
            return holder.isConfirmed();
        }
        String controllerClass = controller == null ? "null" : controller.getClass().getName();
        System.err.println("[ScreenNavigator] Exit dialog controller does not implement ExitConfirmResultHolder: "
            + controllerClass);
        return false;
    }

    // marker interfaces — controllers implement these so the navigator can inject the stage without hard-casting

    /** Implemented by screen controllers that hold resources (timelines, listeners, bindings) needing release before navigation. */
    public interface Cleanable {
        void cleanup();
    }

    /** Implemented by every dialog controller that needs to close itself. */
    public interface DialogController {
        void setDialogStage(Stage stage);
    }

    /** Implemented by the exit-confirm dialog controller. */
    public interface ExitConfirmResultHolder {
        boolean isConfirmed();
    }

    // derives a bundle base name from the fxml path and loads it; returns null if no bundle exists
    private static ResourceBundle resolveBundleForFxml(String fxmlPath) {
        if (fxmlPath == null) return null;
        String baseName = fxmlPath
                .replaceFirst("^/", "")
                .replaceFirst("\\.fxml$", "")
                .replace('/', '.');
        try {
            return ResourceBundle.getBundle(baseName);
        } catch (MissingResourceException ignored) {
            return null;
        }
    }
}
