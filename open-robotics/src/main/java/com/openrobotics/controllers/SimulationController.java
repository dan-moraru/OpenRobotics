package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.Station;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.util.ScreenNavigator;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

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

    // ── Engine binding ────────────────────────────────────────────────────
    private SimulationEngine engine;
    private Timeline         simLoop;
    private double           speedFactor  = 1.0;
    private int              localTick    = 0;
    private static final double BASE_TICK_MS = 100.0;

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
        // Bind engine from shared AppState
        engine = AppState.getEngine();
        if (engine == null && AppState.hasConfigPath()) {
            engine = new SimulationEngine(AppState.getConfigPath());
            if (engine.getMap() == null) {
                log("\u26a0 Config reload failed: " + (engine.getInitError() != null ? engine.getInitError() : "unknown error"));
                if (viewportStatusLabel != null) {
                    viewportStatusLabel.setText("Load failed");
                }
                return;
            }
            AppState.setEngine(engine);
        }
        if (engine != null && engine.getMap() != null) {
            populateOutliner();
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("Loaded " + engine.getMap().getEntities().size() + " objects");
            }
            log("Loaded simulation with " + engine.getMap().getEntities().size() + " entities. Press \u25b6 to start.");
            drawViewport();
        } else {
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("No config loaded");
            }
            log("No configuration loaded. Go to Setup \u2192 Load Config first.");
        }
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

        drawEntities(gc);
    }

    /** Renders every entity from the engine onto the canvas. */
    private void drawEntities(GraphicsContext gc) {
        if (engine == null || engine.getMap() == null) return;
        double tileSize = 32 * zoom;
        double pad = Math.max(1.0, tileSize * 0.06);

        for (MapEntity entity : engine.getMap().getEntities()) {
            double sx = viewOffsetX + entity.getPosition().getX() * tileSize;
            double sy = viewOffsetY + entity.getPosition().getY() * tileSize;

            if (entity instanceof Robot robot) {
                gc.setFill(Color.web("#4A90E2"));
                gc.fillRoundRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad, 5, 5);
                Color dot = switch (robot.getState()) {
                    case MOVING            -> Color.web("#2ECC71");
                    case CHARGING          -> Color.web("#F1C40F");
                    case LOADING, UNLOADING -> Color.web("#9B59B6");
                    default                -> Color.web("#95A5A6");
                };
                gc.setFill(dot);
                double r = Math.max(3.0, tileSize * 0.15);
                gc.fillOval(sx + tileSize - r * 2 - pad, sy + pad, r * 2, r * 2);
            } else if (entity instanceof Rack) {
                gc.setFill(Color.web("#E8A020"));
                gc.fillRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            } else if (entity instanceof Station) {
                gc.setFill(Color.web("#27AE60"));
                gc.fillRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            } else if (entity instanceof Obstacle) {
                gc.setFill(Color.web("#5D5B54"));
                gc.fillRect(sx, sy, tileSize, tileSize);
            } else {
                // Engine stores all non-robot entities as plain MapEntity;
                // infer visual type from name so items are colour-coded.
                String n = entity.getName().toLowerCase();
                boolean isWall = n.contains("wall") || n.contains("obstacle");
                if (n.contains("rack") || n.contains("shelf")) {
                    gc.setFill(Color.web("#E8A020"));
                } else if (n.contains("station") || n.contains("charge") || n.contains("depot")
                        || n.contains("pickup") || n.contains("delivery")) {
                    gc.setFill(Color.web("#27AE60"));
                } else if (isWall) {
                    gc.setFill(Color.web("#5D5B54"));
                } else {
                    gc.setFill(Color.web("#BDC3C7"));
                }
                if (isWall) gc.fillRect(sx, sy, tileSize, tileSize);
                else        gc.fillRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            }

            if (zoom >= 0.8 && tileSize >= 18) {
                String lbl = entity.getName().length() > 5
                        ? entity.getName().substring(0, 4) + "\u2026"
                        : entity.getName();
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font(Math.max(7.0, tileSize * 0.26)));
                gc.fillText(lbl, sx + pad + 1, sy + tileSize - pad - 2);
            }
        }
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
        populateOutliner();
    }

    /** Rebuilds the outliner list from the current engine map. */
    private void populateOutliner() {
        if (outlinerListView == null || engine == null) return;
        String filter = (outlinerSearchField != null && outlinerSearchField.getText() != null)
                ? outlinerSearchField.getText().toLowerCase() : "";
        outlinerListView.getItems().clear();
        int count = 0;
        for (MapEntity e : engine.getMap().getEntities()) {
            String icon  = (e instanceof Robot) ? "\ud83e\udd16 "
                         : (e instanceof Rack)  ? "\ud83d\udce6 "
                         : (e instanceof Station) ? "\u26a1 "
                         : (e instanceof Obstacle) ? "\ud83e\uddf1 " : "\u25ab ";
            String entry = icon + e.getName() + "  " + e.getPosition();
            if (filter.isEmpty() || entry.toLowerCase().contains(filter))
                outlinerListView.getItems().add(entry);
            count++;
        }
        if (objsLabel != null) objsLabel.setText("objs: " + count);
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
        if (engine == null) {
            log("\u26a0 No simulation loaded. Return to Setup and load a config.");
            return;
        }
        if (!running) {
            running = true;
            paused  = false;
            if (simStatusLabel != null) {
                simStatusLabel.setText("RUNNING");
                simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
            }
            startLoop();
            log("Simulation started.");
        } else if (paused) {
            paused = false;
            if (simStatusLabel != null) {
                simStatusLabel.setText("RUNNING");
                simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
            }
            startLoop();
            log("Simulation resumed.");
        }
    }

    @FXML
    private void onPause() {
        if (running && !paused) {
            paused = true;
            stopLoop();
            if (simStatusLabel != null) {
                simStatusLabel.setText("PAUSED");
                simStatusLabel.setStyle("-fx-text-fill: #E0B200; -fx-font-weight: bold;");
            }
            log("Simulation paused.");
        }
    }

    @FXML
    private void onStop() {
        running = false;
        paused  = false;
        stopLoop();
        if (simStatusLabel != null) {
            simStatusLabel.setText("STOPPED");
            simStatusLabel.setStyle("-fx-text-fill: #D6453D; -fx-font-weight: bold;");
        }
        log("Simulation stopped.");
    }

    @FXML
    private void onRestart() {
        onStop();
        localTick = 0;
        if (AppState.getConfigPath() != null) {
            engine = new SimulationEngine(AppState.getConfigPath());
            AppState.setEngine(engine);
        }
        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
        if (simProgressBar != null) simProgressBar.setProgress(0);
        if (simStatusLabel != null) {
            simStatusLabel.setText("READY");
            simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
        }
        log("Simulation reset.");
        populateOutliner();
        drawViewport();
    }

    @FXML
    private void onNextFrame() {
        if (engine == null) { log("\u26a0 No simulation loaded."); return; }
        if (!running) {
            running = true;
            if (simStatusLabel != null) {
                simStatusLabel.setText("STEPPING");
                simStatusLabel.setStyle("-fx-text-fill: #E0B200; -fx-font-weight: bold;");
            }
        }
        doTick();
        log("Step \u2192 TICK " + localTick);
    }

    @FXML private void onSpeed1() { setSpeed(1);  log("Speed set to ×1."); }
    @FXML private void onSpeed2() { setSpeed(2);  log("Speed set to ×2."); }
    @FXML private void onSpeed3() { setSpeed(3);  log("Speed set to ×3."); }

    private void setSpeed(int factor) {
        speedFactor = factor;
        if (running && !paused) startLoop();
    }

    // ------------------------------------------------------------------ //
    //  Simulation loop helpers
    // ------------------------------------------------------------------ //

    private void startLoop() {
        if (simLoop != null) simLoop.stop();
        simLoop = new Timeline(new KeyFrame(
                Duration.millis(BASE_TICK_MS / speedFactor),
                e -> doTick()
        ));
        simLoop.setCycleCount(Animation.INDEFINITE);
        simLoop.play();
    }

    private void stopLoop() {
        if (simLoop != null) {
            simLoop.stop();
            simLoop = null;
        }
    }

    private void doTick() {
        if (engine == null) return;
        engine.tick();
        localTick++;
        drawViewport();
        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK " + localTick);
        if (simProgressBar != null) simProgressBar.setProgress(Math.min(1.0, localTick / 1000.0));
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
        System.out.println("[SimulationController] " + message);
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

