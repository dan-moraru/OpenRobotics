package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for {@code SimulationScreen.fxml}.
 *
 * <p>Manages the simulation viewport and all UI controls (§4.1.3 A + B):
 * <ul>
 *   <li>2-D warehouse canvas rendering</li>
 *   <li>Drag-and-drop of object tiles from sidebar onto the canvas</li>
 *   <li>Drag existing objects within the canvas to reposition them</li>
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

    // ── LIVE STATS (not yet in FXML – guarded with null checks) ──────────
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

    // ── Object images ─────────────────────────────────────────────────────
    private final Map<String, javafx.scene.image.Image> objectImages = new HashMap<>();

    // ── Canvas object state ───────────────────────────────────────────────
    private final List<CanvasObject> objects = new ArrayList<>();
    private CanvasObject selectedObject  = null;
    private CanvasObject draggingOnCanvas = null;  // object being moved within canvas
    private CanvasObject clipboard = null;
    private int nextObjId = 1;

    // ------------------------------------------------------------------ //
    //  Canvas object model
    // ------------------------------------------------------------------ //

    /** Represents an object placed on the 2D warehouse canvas. */
    private static class CanvasObject {
        String type;
        String name;
        int tileX;
        int tileY;
        boolean selected;

        CanvasObject(String type, int tileX, int tileY, int id) {
            this.type  = type;
            this.tileX = tileX;
            this.tileY = tileY;
            this.name  = type.toLowerCase() + "_" + id;
        }

        /** Width of the object in tiles. */
        int widthTiles()  { return 1; }
        /** Height of the object in tiles. */
        int heightTiles() { return 1; }
    }

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Bind canvas size to the viewport stack so it fills the pane
        warehouseCanvas.widthProperty().bind(viewportStack.widthProperty());
        warehouseCanvas.heightProperty().bind(viewportStack.heightProperty());

        // Redraw whenever size changes
        warehouseCanvas.widthProperty().addListener(e -> drawViewport());
        warehouseCanvas.heightProperty().addListener(e -> drawViewport());

        // Viewport mouse interactions
        viewportStack.setOnMousePressed(this::onViewportMousePressed);
        viewportStack.setOnMouseDragged(this::onViewportMouseDragged);
        viewportStack.setOnMouseReleased(this::onViewportMouseReleased);
        viewportStack.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            zoom = Math.max(0.2, Math.min(zoom * factor, 10.0));
            drawViewport();
        });

        // Drop target: accept objects dragged from the Add Object sidebar
        viewportStack.setOnDragOver(this::onCanvasDragOver);
        viewportStack.setOnDragDropped(this::onCanvasDragDropped);
        viewportStack.setOnDragExited(e -> {
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
        });

        // Keyboard shortcuts: Delete, Cmd+C, Cmd+V
        viewportStack.setFocusTraversable(true);
        viewportStack.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DELETE, BACK_SPACE -> deleteSelected();
                case C -> { if (e.isShortcutDown()) copySelected(); }
                case V -> { if (e.isShortcutDown()) pasteClipboard(); }
                default -> {}
            }
            e.consume();
        });
        // Outliner search filter
        if (outlinerSearchField != null)
            outlinerSearchField.textProperty().addListener((obs, o, n) -> filterOutliner(n));

        // Pre-load object icons
        for (String type : List.of("ROBOT", "CHARGER", "STATION", "DOCK", "WALL", "SHELF")) {
            var stream = getClass().getResourceAsStream(
                    "/com/openrobotics/img/" + type.toLowerCase() + ".png");
            if (stream != null)
                objectImages.put(type, new javafx.scene.image.Image(stream));
        }

        if (editModeLabel    != null) editModeLabel.setText("edit mode");
        if (viewportModeLabel != null) viewportModeLabel.setText("drag");

        log("Simulation screen ready. Drag an object from the panel into the viewport.");
    }

    // ------------------------------------------------------------------ //
    //  Viewport rendering
    // ------------------------------------------------------------------ //

    /** Draws the warehouse grid and all placed objects on the canvas. */
    private void drawViewport() {
        GraphicsContext gc = warehouseCanvas.getGraphicsContext2D();
        double w = warehouseCanvas.getWidth();
        double h = warehouseCanvas.getHeight();

        // Background
        gc.setFill(Color.web("#CDCBC3"));
        gc.fillRect(0, 0, w, h);

        // Grid lines
        gc.setStroke(Color.web("#B0ADA5"));
        gc.setLineWidth(0.5);
        double tileSize = 32 * zoom;
        for (double x = viewOffsetX % tileSize; x < w; x += tileSize)
            gc.strokeLine(x, 0, x, h);
        for (double y = viewOffsetY % tileSize; y < h; y += tileSize)
            gc.strokeLine(0, y, w, y);

        // Centre crosshair
        gc.setStroke(Color.web("#5D5B54"));
        gc.setLineWidth(1.0);
        gc.strokeLine(w / 2 - 10, h / 2, w / 2 + 10, h / 2);
        gc.strokeLine(w / 2, h / 2 - 10, w / 2, h / 2 + 10);

        // Placed objects
        for (CanvasObject obj : objects) {
            drawObject(gc, obj, tileSize);
        }
    }

    /** Draws a single canvas object at its tile position. */
    private void drawObject(GraphicsContext gc, CanvasObject obj, double tileSize) {
        double px = obj.tileX * tileSize + viewOffsetX;
        double py = obj.tileY * tileSize + viewOffsetY;
        double ow = obj.widthTiles()  * tileSize;
        double oh = obj.heightTiles() * tileSize;

        // Skip if fully out of view
        double cw = warehouseCanvas.getWidth();
        double ch = warehouseCanvas.getHeight();
        if (px + ow < 0 || px > cw || py + oh < 0 || py > ch) return;

        // Draw icon or fall back to a coloured rectangle
        javafx.scene.image.Image img = objectImages.get(obj.type);
        if (img != null && !img.isError()) {
            gc.drawImage(img, px, py, ow, oh);
        } else {
            gc.setFill(Color.web(objectBodyColor(obj.type)));
            gc.fillRect(px, py, ow, oh);
        }

        // Selection highlight border
        if (obj.selected) {
            gc.setStroke(Color.web("#7DD4A8"));
            gc.setLineWidth(2.0);
            gc.strokeRect(px + 1, py + 1, ow - 2, oh - 2);
        }
    }

    /** Returns the fill colour for each object type. */
    private String objectBodyColor(String type) {
        return switch (type) {
            case "ROBOT"   -> "#2E5F7A";
            case "CHARGER" -> "#7A6020";
            case "STATION" -> "#4A7050";
            case "DOCK"    -> "#6A4828";
            case "WALL"    -> "#3D3C39";
            case "SHELF"   -> "#52504A";
            default        -> "#5D5B54";
        };
    }

    // ------------------------------------------------------------------ //
    //  Drag FROM sidebar tile → canvas  (JavaFX DnD API)
    // ------------------------------------------------------------------ //

    /**
     * Fired when the user starts dragging an object tile button.
     * Puts the object type string into the dragboard so the canvas can receive it.
     */
    @FXML
    private void onObjectTileDragDetected(MouseEvent e) {
        Button source = (Button) e.getSource();
        String type = (String) source.getUserData();

        Dragboard db = source.startDragAndDrop(TransferMode.COPY);
        ClipboardContent content = new ClipboardContent();
        content.putString(type);
        db.setContent(content);

        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(javafx.scene.paint.Color.TRANSPARENT);
        db.setDragView(source.getGraphic().snapshot(params, null), e.getX(), e.getY());

        if (viewportStatusLabel != null)
            viewportStatusLabel.setText("dragging(" + type.toLowerCase() + ")");

        e.consume();
    }

    /** Accept the drag as long as the dragboard carries an object-type string. */
    private void onCanvasDragOver(DragEvent e) {
        if (e.getDragboard().hasString()) {
            e.acceptTransferModes(TransferMode.COPY);
            // Update status label with live position feedback
            if (viewportStatusLabel != null) {
                double tileSize = 32 * zoom;
                int tx = (int) Math.floor((e.getX() - viewOffsetX) / tileSize);
                int ty = (int) Math.floor((e.getY() - viewOffsetY) / tileSize);
                viewportStatusLabel.setText(
                        "dragging(" + e.getDragboard().getString().toLowerCase()
                        + ")  →  (" + tx + ", " + ty + ")");
            }
        }
        e.consume();
    }

    /** Creates a new CanvasObject at the tile where the user dropped. */
    private void onCanvasDragDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (db.hasString()) {
            String type     = db.getString();
            double tileSize = 32 * zoom;
            int tx = (int) Math.floor((e.getX() - viewOffsetX) / tileSize);
            int ty = (int) Math.floor((e.getY() - viewOffsetY) / tileSize);

            CanvasObject obj = new CanvasObject(type, tx, ty, nextObjId++);
            objects.add(obj);
            selectObject(obj);
            updateOutliner();
            drawViewport();

            log("Added " + obj.name + " at tile (" + tx + ", " + ty + ").");
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            e.setDropCompleted(true);
        }
        e.consume();
    }

    // ------------------------------------------------------------------ //
    //  Drag existing object WITHIN canvas  (mouse events)
    // ------------------------------------------------------------------ //

    private void onViewportMousePressed(MouseEvent e) {
        viewportStack.requestFocus();
        lastMouseX = e.getX();
        lastMouseY = e.getY();

        if (e.getButton() == MouseButton.PRIMARY) {
            CanvasObject hit = objectAtScreenPos(e.getX(), e.getY());
            if (hit != null) {
                selectObject(hit);
                draggingOnCanvas = hit;
            } else {
                selectObject(null);
                draggingOnCanvas = null;
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            draggingOnCanvas = null;
        }
    }

    private void onViewportMouseDragged(MouseEvent e) {
        double dx = e.getX() - lastMouseX;
        double dy = e.getY() - lastMouseY;

        if (draggingOnCanvas != null && e.getButton() == MouseButton.PRIMARY) {
            // Move the selected object – snap to nearest tile
            double tileSize = 32 * zoom;
            int newTX = (int) Math.floor((e.getX() - viewOffsetX) / tileSize);
            int newTY = (int) Math.floor((e.getY() - viewOffsetY) / tileSize);
            draggingOnCanvas.tileX = newTX;
            draggingOnCanvas.tileY = newTY;
            if (viewportStatusLabel != null)
                viewportStatusLabel.setText(
                        "dragging(" + draggingOnCanvas.name
                        + ")  →  (" + newTX + ", " + newTY + ")");
            drawViewport();
        } else {
            // Pan the viewport
            viewOffsetX += dx;
            viewOffsetY += dy;
            drawViewport();
        }

        lastMouseX = e.getX();
        lastMouseY = e.getY();
    }

    private void onViewportMouseReleased(MouseEvent e) {
        if (draggingOnCanvas != null) {
            log("Moved " + draggingOnCanvas.name
                + " to tile (" + draggingOnCanvas.tileX + ", " + draggingOnCanvas.tileY + ").");
            draggingOnCanvas = null;
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            if (viewportModeLabel   != null) viewportModeLabel.setText("drag");
        }
    }

    // ------------------------------------------------------------------ //
    //  Hit-testing
    // ------------------------------------------------------------------ //

    /** Returns the topmost object whose rendered area contains (sx, sy), or null. */
    private CanvasObject objectAtScreenPos(double sx, double sy) {
        double tileSize = 32 * zoom;
        for (int i = objects.size() - 1; i >= 0; i--) {
            CanvasObject obj = objects.get(i);
            double px = obj.tileX * tileSize + viewOffsetX;
            double py = obj.tileY * tileSize + viewOffsetY;
            double ow = obj.widthTiles()  * tileSize;
            double oh = obj.heightTiles() * tileSize;
            if (sx >= px && sx < px + ow && sy >= py && sy < py + oh)
                return obj;
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Selection & Properties panel
    // ------------------------------------------------------------------ //

    private void deleteSelected() {
        if (selectedObject == null) return;
        log("Deleted " + selectedObject.name + ".");
        objects.remove(selectedObject);
        selectedObject = null;
        updateOutliner();
        drawViewport();
    }

    private void copySelected() {
        if (selectedObject == null) return;
        clipboard = selectedObject;
        log("Copied " + clipboard.name + ".");
    }

    private void pasteClipboard() {
        if (clipboard == null) return;
        CanvasObject copy = new CanvasObject(clipboard.type, clipboard.tileX + 1, clipboard.tileY + 1, nextObjId++);
        objects.add(copy);
        selectObject(copy);
        updateOutliner();
        drawViewport();
        log("Pasted " + copy.name + " at tile (" + copy.tileX + ", " + copy.tileY + ").");
    }

    private void selectObject(CanvasObject obj) {
        if (selectedObject != null) selectedObject.selected = false;
        selectedObject = obj;

        if (obj != null) {
            obj.selected = true;
            // Sync outliner highlight
            int idx = objects.indexOf(obj);
            if (outlinerListView != null && idx >= 0)
                outlinerListView.getSelectionModel().select(idx);
            showPropertiesFor(obj);
        } else {
            if (outlinerListView != null)
                outlinerListView.getSelectionModel().clearSelection();
            clearPropertiesPanel();
        }
        drawViewport();
    }

    private void showPropertiesFor(CanvasObject obj) {
        if (propertiesPanel == null) return;
        propertiesPanel.getChildren().clear();
        propertiesPanel.getChildren().add(
                new Label("Type:  " + obj.type));
        propertiesPanel.getChildren().add(
                new Label("Name:  " + obj.name));
        propertiesPanel.getChildren().add(
                new Label("Tile X: " + obj.tileX));
        propertiesPanel.getChildren().add(
                new Label("Tile Y: " + obj.tileY));
    }

    private void clearPropertiesPanel() {
        if (propertiesPanel == null) return;
        propertiesPanel.getChildren().clear();
        propertiesPanel.getChildren().add(
                new Label("Select an object."));
    }

    // ------------------------------------------------------------------ //
    //  Add Object / Outliner
    // ------------------------------------------------------------------ //

    /**
     * Handles a click on one of the Add-Object tiles (§4.1.3 B, zone 1).
     * A single click logs the selection; a double-click opens the Object Description dialog.
     */
    @FXML
    private void onAddObjectClick(MouseEvent e) {
        Button source = (Button) e.getSource();
        String type = (String) source.getUserData();

        if (e.getClickCount() == 2) {
            ScreenNavigator.openDialog(
                    ScreenNavigator.DIALOG_OBJECT_DESC, type + " – Description");
        } else {
            log("Selected object type: " + type + ".  Drag it into the viewport to place.");
        }
    }

    /** Handles selection in the Outliner list. */
    @FXML
    private void onOutlinerSelect(MouseEvent e) {
        if (outlinerListView == null) return;
        int idx = outlinerListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < objects.size())
            selectObject(objects.get(idx));
    }

    private void filterOutliner(String query) {
        // TODO Sprint 3+: filter outliner items by name
    }

    /** Refreshes the Outliner list and the objs: counter. */
    private void updateOutliner() {
        if (outlinerListView != null) {
            outlinerListView.getItems().clear();
            for (CanvasObject obj : objects)
                outlinerListView.getItems().add(obj.type + ":  " + obj.name);
        }
        if (objsLabel != null) objsLabel.setText("objs: " + objects.size());
    }

    // ------------------------------------------------------------------ //
    //  Properties panel reset
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
            if (simStatusLabel != null) {
                simStatusLabel.setText("RUNNING");
                simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
            }
            log("Simulation started.");
        } else if (paused) {
            paused = false;
            if (simStatusLabel != null) simStatusLabel.setText("RUNNING");
            log("Simulation resumed.");
        }
    }

    @FXML
    private void onPause() {
        if (running && !paused) {
            paused = true;
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
        if (simStatusLabel != null) {
            simStatusLabel.setText("STOPPED");
            simStatusLabel.setStyle("-fx-text-fill: #D6453D; -fx-font-weight: bold;");
        }
        log("Simulation stopped.");
    }

    @FXML
    private void onRestart() {
        onStop();
        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
        if (simProgressBar   != null) simProgressBar.setProgress(0);
        if (simStatusLabel   != null) {
            simStatusLabel.setText("READY");
            simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
        }
        log("Simulation reset.");
        drawViewport();
    }

    @FXML
    private void onNextFrame() {
        log("Step → next frame.");
        // TODO Sprint 4+: advance simulation by one tick
    }

    @FXML private void onSpeed1() { setSpeed(1); log("Speed set to ×1."); }
    @FXML private void onSpeed2() { setSpeed(2); log("Speed set to ×2."); }
    @FXML private void onSpeed3() { setSpeed(3); log("Speed set to ×3."); }

    private void setSpeed(int factor) {
        // TODO Sprint 4+: pass speed factor to the simulation engine
    }

    // ------------------------------------------------------------------ //
    //  Viewport zoom buttons
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

