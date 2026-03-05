package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller for {@code SimulationScreen.fxml}.
 *
 * <p>Manages the simulation viewport and all UI controls (§4.1.3 A + B):
 * <ul>
 *   <li>2-D warehouse canvas rendering</li>
 *   <li>Outliner: Add Object / Outliner tabs</li>
 *   <li>Object properties panel</li>
 *   <li>Console output</li>
 *   <li>Playback controls: play / pause / stop / restart / speed</li>
 *   <li>Live statistics: tick, tasks done, collisions, status</li>
 *   <li>Viewport navigation (right-click pan, scroll zoom)</li>
 * </ul>
 */
public class SimulationController {

    // ── TOPBAR LIVE STATS ────────────────────────────────────────────────
    @FXML private Label objsLabel;
    @FXML private Label ramLabel;
    @FXML private Label canvasSizeLabel;

    // ── LIVE STATS (legacy names kept for compatibility) ─────────────────
    @FXML private Label tickLabel;
    @FXML private Label tasksDoneLabel;
    @FXML private Label collisionsLabel;
    @FXML private Label simStatusLabel;

    // ── OUTLINER ────────────────────────────────────────────────────────
    @FXML private TabPane          outlinerTabPane;
    @FXML private ListView<String> outlinerListView;
    @FXML private TextField        outlinerSearchField;

    // ── VIEWPORT ────────────────────────────────────────────────────────
    @FXML private StackPane viewportStack;
    @FXML private Canvas    warehouseCanvas;
    @FXML private Label     viewportModeLabel;
    @FXML private Label     editModeLabel;
    @FXML private Label     viewportStatusLabel;
    @FXML private Label     tickDisplayLabel;

    // ── PROPERTIES PANEL ────────────────────────────────────────────────
    @FXML private VBox propertiesPanel;
    @FXML private Label propDescLabel;
    @FXML private TextField strPropField;

    // ── CONSOLE ─────────────────────────────────────────────────────────
    @FXML private TextArea consoleArea;

    // ── PLAYBACK ────────────────────────────────────────────────────────
    @FXML private Button      speed1Btn;
    @FXML private Button      speed2Btn;
    @FXML private Button      speed3Btn;
    @FXML private ProgressBar simProgressBar;

    // ── Viewport navigation state ─────────────────────────────────────────
    private double lastMouseX;
    private double lastMouseY;
    private double viewOffsetX = 0;
    private double viewOffsetY = 0;
    private double zoom        = 1.0;

    // ── Simulation state ──────────────────────────────────────────────────
    private boolean running = false;
    private boolean paused  = false;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Bind canvas size to the viewport stack so it fills the pane
        warehouseCanvas.widthProperty().bind(viewportStack.widthProperty());
        warehouseCanvas.heightProperty().bind(viewportStack.heightProperty());

        // Redraw whenever size changes
        warehouseCanvas.widthProperty().addListener(e  -> drawViewport());
        warehouseCanvas.heightProperty().addListener(e -> drawViewport());

        // Viewport mouse interactions (§4.1.3 B, zone 4)
        viewportStack.setOnMousePressed(this::onViewportMousePressed);
        viewportStack.setOnMouseDragged(this::onViewportMouseDragged);
        viewportStack.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            zoom = Math.max(0.2, Math.min(zoom * factor, 10.0));
            drawViewport();
        });

        // Outliner search filter
        if (outlinerSearchField != null)
            outlinerSearchField.textProperty().addListener((obs, o, n) -> filterOutliner(n));

        log("Simulation screen ready. Configure and press ▶ to begin.");
    }

    // ------------------------------------------------------------------ //
    //  Viewport rendering
    // ------------------------------------------------------------------ //

    /** Draws the warehouse grid and all objects on the canvas. */
    private void drawViewport() {
        var gc = warehouseCanvas.getGraphicsContext2D();
        double w = warehouseCanvas.getWidth();
        double h = warehouseCanvas.getHeight();

        // Background
        gc.setFill(javafx.scene.paint.Color.web("#CDCBC3"));
        gc.fillRect(0, 0, w, h);

        // TODO Sprint 3+: render tile grid, robots, POIs using the simulation model.
        // Placeholder grid
        gc.setStroke(javafx.scene.paint.Color.web("#B0ADA5"));
        gc.setLineWidth(0.5);
        double tileSize = 32 * zoom;
        for (double x = viewOffsetX % tileSize; x < w; x += tileSize)
            gc.strokeLine(x, 0, x, h);
        for (double y = viewOffsetY % tileSize; y < h; y += tileSize)
            gc.strokeLine(0, y, w, y);

        // Centre crosshair
        gc.setStroke(javafx.scene.paint.Color.web("#5D5B54"));
        gc.setLineWidth(1.0);
        gc.strokeLine(w / 2 - 10, h / 2, w / 2 + 10, h / 2);
        gc.strokeLine(w / 2, h / 2 - 10, w / 2, h / 2 + 10);
    }

    // ------------------------------------------------------------------ //
    //  Viewport navigation (§4.1.3 B, zone 4)
    // ------------------------------------------------------------------ //

    private void onViewportMousePressed(MouseEvent e) {
        lastMouseX = e.getX();
        lastMouseY = e.getY();
        if (e.getButton() == MouseButton.SECONDARY && viewportModeLabel != null)
            viewportModeLabel.setText("Movement Mode  [WASD / drag]");
    }

    private void onViewportMouseDragged(MouseEvent e) {
        if (e.getButton() == MouseButton.SECONDARY || e.getButton() == MouseButton.PRIMARY) {
            viewOffsetX += e.getX() - lastMouseX;
            viewOffsetY += e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            drawViewport();
        }
    }

    // ------------------------------------------------------------------ //
    //  Add Object / Outliner
    // ------------------------------------------------------------------ //

    /**
     * Handles a click on one of the Add-Object tiles (§4.1.3 B, zone 1).
     * A single click selects; a double-click opens the Object Description dialog.
     */
    @FXML
    private void onAddObjectClick(MouseEvent e) {
        Button source = (Button) e.getSource();
        String type = (String) source.getUserData();

        if (e.getClickCount() == 2) {
            // Open description dialog (§4.2 – Specialty Screen 3)
            var loader = ScreenNavigator.openDialog(
                    ScreenNavigator.DIALOG_OBJECT_DESC, type + " – Description");
            // TODO Sprint 3+: pass object type to ObjectDescController
        } else {
            log("Selected object type: " + type);
        }
    }

    /** Handles selection in the Outliner list. */
    @FXML
    private void onOutlinerSelect(MouseEvent e) {
        if (outlinerListView == null) return;
        String selected = outlinerListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            log("Selected: " + selected);
            updatePropertiesPanel(selected);
        }
    }

    private void filterOutliner(String query) {
        // TODO Sprint 3+: filter outliner items by name
    }

    private void updatePropertiesPanel(String objectName) {
        if (propertiesPanel == null) return;
        propertiesPanel.getChildren().clear();
        propertiesPanel.getChildren().add(new Label("Object: " + objectName));
        // TODO Sprint 3+: populate real property rows from the model
    }

    // ------------------------------------------------------------------ //
    //  Properties panel
    // ------------------------------------------------------------------ //

    @FXML
    private void onResetStringProp() {
        if (strPropField != null) strPropField.setText("Hello");
    }

    // ------------------------------------------------------------------ //
    //  Playback Controls (§4.1.3 B, zone 5)
    // ------------------------------------------------------------------ //

    @FXML
    private void onPlay() {
        if (!running) {
            running = true;
            paused  = false;
            simStatusLabel.setText("RUNNING");
            simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
            log("Simulation started.");
            // TODO Sprint 4+: start the simulation engine tick loop
        } else if (paused) {
            paused = false;
            simStatusLabel.setText("RUNNING");
            log("Simulation resumed.");
        }
    }

    @FXML
    private void onPause() {
        if (running && !paused) {
            paused = true;
            simStatusLabel.setText("PAUSED");
            simStatusLabel.setStyle("-fx-text-fill: #E0B200; -fx-font-weight: bold;");
            log("Simulation paused.");
        }
    }

    @FXML
    private void onStop() {
        running = false;
        paused  = false;
        simStatusLabel.setText("STOPPED");
        simStatusLabel.setStyle("-fx-text-fill: #D6453D; -fx-font-weight: bold;");
        log("Simulation stopped.");
        // TODO Sprint 4+: stop the engine and persist partial results
    }

    @FXML
    private void onRestart() {
        onStop();
        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
        if (simProgressBar != null) simProgressBar.setProgress(0);
        simStatusLabel.setText("READY");
        simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
        log("Simulation reset.");
        drawViewport();
    }

    @FXML
    private void onNextFrame() {
        log("Step → next frame.");
        // TODO Sprint 4+: advance simulation by one tick
    }

    @FXML private void onSpeed1() { setSpeed(1);  log("Speed set to ×1."); }
    @FXML private void onSpeed2() { setSpeed(2);  log("Speed set to ×2."); }
    @FXML private void onSpeed3() { setSpeed(3);  log("Speed set to ×3."); }

    private void setSpeed(int factor) {
        // TODO Sprint 4+: pass speed factor to the simulation engine
    }

    // ------------------------------------------------------------------ //
    //  Viewport zoom buttons (§4.1.3 B, zone 5)
    // ------------------------------------------------------------------ //

    @FXML private void onZoomIn()  { zoom = Math.min(zoom * 1.2, 10.0); drawViewport(); }
    @FXML private void onZoomOut() { zoom = Math.max(zoom / 1.2, 0.2);  drawViewport(); }

    // ------------------------------------------------------------------ //
    //  Console
    // ------------------------------------------------------------------ //

    @FXML
    private void onClearConsole() {
        if (consoleArea != null) consoleArea.clear();
    }

    /** Appends a line to the in-app console. */
    private void log(String message) {
        if (consoleArea != null) consoleArea.appendText(message + "\n");
    }

    // ------------------------------------------------------------------ //
    //  Navigation & Menu
    // ------------------------------------------------------------------ //

    @FXML private void onTabEditor() { /* already on editor tab */ }

    @FXML
    private void onMenuWelcome() { ScreenNavigator.goToWelcome(); }

    @FXML
    private void onMenuCalculateResults() { ScreenNavigator.goToResults(); }

    @FXML
    private void onMenuGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com"));
        } catch (Exception ex) { log("Could not open browser."); }
    }

    @FXML
    private void onViewResults() { ScreenNavigator.goToResults(); }

    @FXML
    private void onExit() {
        if (ScreenNavigator.confirmExit()) javafx.application.Platform.exit();
    }
}

