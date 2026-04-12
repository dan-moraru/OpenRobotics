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

/**
 * Utility class that centralises all screen/dialog transitions.
 *
 * <p>Every screen is loaded from its FXML resource. The primary {@link Stage}
 * is held as a static reference so any controller can call
 * {@code ScreenNavigator.loadScreen(...)} without passing the stage around.
 */
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

    /** The application's primary stage – set once in {@link com.openrobotics.MainApp}. */
    private static Stage primaryStage;

    /** Holds the controller of the currently displayed screen so it can be cleaned up on navigation. */
    private static Object currentController;

    private ScreenNavigator() {}

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    /** Called once from {@link com.openrobotics.MainApp#start(Stage)}. */
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // ------------------------------------------------------------------ //
    //  Full-screen navigation
    // ------------------------------------------------------------------ //

    /**
     * Loads and displays the given FXML as the primary scene (full screen).
     *
     * @param fxmlPath one of the path constants defined in this class
     */
    public static void loadScreen(String fxmlPath) {
        try {
            // Clean up the previous screen's controller before replacing it
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

    // ------------------------------------------------------------------ //
    //  Modal dialogs
    // ------------------------------------------------------------------ //

    /**
     * Opens the given FXML as a blocking modal dialog.
     *
     * @param fxmlPath one of the {@code DIALOG_*} constants
     * @return the {@link FXMLLoader} after the dialog is closed
     *         (lets callers retrieve the controller for result data)
     */
    public static FXMLLoader openDialog(String fxmlPath) {
        return openDialog(fxmlPath, "");
    }

    /**
     * Opens a modal dialog with a custom title.
     */
    public static FXMLLoader openDialog(String fxmlPath, String title) {
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

            Scene scene = new Scene(root, Color.TRANSPARENT);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

            return loader;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open dialog: " + fxmlPath, e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Convenience shortcuts
    // ------------------------------------------------------------------ //

    /** Navigate to the Welcome screen. */
    public static void goToWelcome()    { loadScreen(WELCOME); }
    /** Navigate to the Setup screen. */
    public static void goToSetup()      { loadScreen(SETUP); }
    /** Navigate to the Simulation screen. */
    public static void goToSimulation() { loadScreen(SIMULATION); }
    /** Navigate to the Results screen. */
    public static void goToResults()    { loadScreen(RESULTS); }

    /** Show the Exit-confirmation dialog; returns {@code true} if user confirmed exit. */
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

    // ------------------------------------------------------------------ //
    //  Marker interfaces – controllers implement these so the navigator
    //  can inject the stage without hard-casting to every concrete type.
    // ------------------------------------------------------------------ //

    /**
     * Implemented by screen controllers that hold resources (timelines, listeners, bindings)
     * that must be released before the screen is replaced.
     */
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

    /**
     * Derives a ResourceBundle base name from an FXML resource path and attempts
     * to load it. Returns {@code null} when no matching bundle exists.
     */
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

