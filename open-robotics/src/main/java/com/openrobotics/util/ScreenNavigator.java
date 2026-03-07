package com.openrobotics.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

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

    /** The application's primary stage – set once in {@link com.openrobotics.MainApp}. */
    private static Stage primaryStage;

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
            System.out.println("[ScreenNavigator] Loading screen: " + fxmlPath);
            FXMLLoader loader = new FXMLLoader(ScreenNavigator.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            primaryStage.show();
            System.out.println("[ScreenNavigator] Loaded screen: " + fxmlPath);
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
            FXMLLoader loader = new FXMLLoader(ScreenNavigator.class.getResource(fxmlPath));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            dialogStage.initStyle(StageStyle.UNDECORATED);
            if (!title.isBlank()) dialogStage.setTitle(title);

            // Pass the stage to the controller so it can close itself
            Object controller = loader.getController();
            if (controller instanceof DialogController dc) {
                dc.setDialogStage(dialogStage);
            }

            Scene scene = new Scene(root);
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
        ExitConfirmResultHolder holder = loader.getController();
        return holder != null && holder.isConfirmed();
    }

    // ------------------------------------------------------------------ //
    //  Marker interfaces – controllers implement these so the navigator
    //  can inject the stage without hard-casting to every concrete type.
    // ------------------------------------------------------------------ //

    /** Implemented by every dialog controller that needs to close itself. */
    public interface DialogController {
        void setDialogStage(Stage stage);
    }

    /** Implemented by the exit-confirm dialog controller. */
    public interface ExitConfirmResultHolder {
        boolean isConfirmed();
    }
}

