package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.map.entities.station.Station;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.util.IconLoader;
import com.openrobotics.util.ScreenNavigator;
import com.openrobotics.util.ViewportTips;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
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
    @FXML private Label     tipLabel;
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
    private static final Color VIEWPORT_BG_COLOR = Color.web("#CDCBC3");
    private static final Color VIEWPORT_GRID_COLOR = Color.web("#B0ADA5");
    private static final Color VIEWPORT_CROSSHAIR_COLOR = Color.web("#5D5B54");
    private static final Color ENTITY_ROBOT_COLOR = Color.web("#4D4B45");
    private static final Color ENTITY_STATE_MOVING_COLOR = Color.web("#599068");
    private static final Color ENTITY_STATE_CHARGING_COLOR = Color.web("#8C7B38");
    private static final Color ENTITY_STATE_LOADING_COLOR = Color.web("#706E65");
    private static final Color ENTITY_STATE_IDLE_COLOR = Color.web("#8D8A7F");
    private static final Color ENTITY_RACK_COLOR = Color.web("#8D8A7F");
    private static final Color ENTITY_STATION_COLOR = Color.web("#599068");
    private static final Color ENTITY_OBSTACLE_COLOR = Color.web("#5D5B54");
    private static final Color ENTITY_FALLBACK_COLOR = Color.web("#8D8A7F");
    private static final Color ENTITY_LABEL_COLOR = Color.web("#C2BEAE");
    private static final Color OBJECT_SELECTION_COLOR = Color.web("#C2BEAE");
    // ── Object rendering ──────────────────────────────────────────────────
    // Uses IconLoader utility for icon caching

    // ── Canvas object state ───────────────────────────────────────────────
    private final List<CanvasObject> objects = new ArrayList<>();
    private final List<Object> outlinerBacking = new ArrayList<>();
    private CanvasObject selectedObject  = null;
    private CanvasObject draggingOnCanvas = null;  // object being moved within canvas
    private CanvasObject clipboard = null;
    private int nextObjId = 1;
    private Timeline tipRotationLoop;

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
        // Bind engine from shared AppState
        engine = AppState.getEngine();
        if (engine == null && AppState.hasConfigPath()) {
            engine = new SimulationEngine(AppState.getConfigPath());
            if (engine == null || engine.getMap() == null) {
                log("\u26a0 Config reload failed: " + (engine.getInitError() != null ? engine.getInitError() : "unknown error"));
                if (viewportStatusLabel != null) {
                    viewportStatusLabel.setText("Load failed");
                }
                return;
            }
            AppState.setEngine(engine);
        }

        if (engine != null && engine.getMap() != null) {
            com.openrobotics.map.Map loadedMap = engine.getMap();
            populateOutliner();
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("Loaded " + loadedMap.getEntities().size() + " objects");
            }
            log("Loaded simulation with " + loadedMap.getEntities().size() + " entities. Press \u25b6 to start.");
            drawViewport();
        } else {
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("No config loaded");
            }
            log("No configuration loaded. Go to Setup \u2192 Load Config first.");
        }

        // Pre-load icons and initialize tips
        IconLoader.preloadAllIcons();
        updateSelectionLabel();
        startTipRotation();

        if (editModeLabel    != null) editModeLabel.setText("edit mode");
        if (viewportModeLabel != null) viewportModeLabel.setText("right-click to pan, left-click to select");
        if (tipLabel != null) tipLabel.setText("TIP: " + ViewportTips.nextTip());

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
        gc.setFill(VIEWPORT_BG_COLOR);
        gc.fillRect(0, 0, w, h);

        // Grid lines
        gc.setStroke(VIEWPORT_GRID_COLOR);
        gc.setLineWidth(0.5);
        double tileSize = 32 * zoom;
        for (double x = viewOffsetX % tileSize; x < w; x += tileSize)
            gc.strokeLine(x, 0, x, h);
        for (double y = viewOffsetY % tileSize; y < h; y += tileSize)
            gc.strokeLine(0, y, w, y);

        // Centre crosshair
        gc.setStroke(VIEWPORT_CROSSHAIR_COLOR);
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

            javafx.scene.image.Image entityIcon = resolveEntityIcon(entity);
            boolean useFullTileIcon = isWallLikeEntity(entity);
            if (entityIcon != null && !entityIcon.isError()) {
                if (useFullTileIcon) {
                    gc.drawImage(entityIcon, sx, sy, tileSize, tileSize);
                } else {
                    gc.drawImage(entityIcon, sx + pad, sy + pad, tileSize - 2 * pad, tileSize - 2 * pad);
                }
            } else if (entity instanceof Robot robot) {
                gc.setFill(ENTITY_ROBOT_COLOR);
                gc.fillRoundRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad, 5, 5);
                Color dot = switch (robot.getState()) {
                    case MOVING             -> ENTITY_STATE_MOVING_COLOR;
                    case CHARGING           -> ENTITY_STATE_CHARGING_COLOR;
                    case LOADING, UNLOADING -> ENTITY_STATE_LOADING_COLOR;
                    default                 -> ENTITY_STATE_IDLE_COLOR;
                };
                gc.setFill(dot);
                double r = Math.max(3.0, tileSize * 0.15);
                gc.fillOval(sx + tileSize - r * 2 - pad, sy + pad, r * 2, r * 2);
            } else if (entity instanceof Rack) {
                gc.setFill(ENTITY_RACK_COLOR);
                gc.fillRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            } else if (entity instanceof Station) {
                gc.setFill(ENTITY_STATION_COLOR);
                gc.fillRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            } else if (entity instanceof Obstacle) {
                gc.setFill(ENTITY_OBSTACLE_COLOR);
                gc.fillRect(sx, sy, tileSize, tileSize);
            } else {
                // Engine stores all non-robot entities as plain MapEntity;
                // infer visual type from name so items are colour-coded.
                String n = entity.getName().toLowerCase();
                boolean isWall = n.contains("wall") || n.contains("obstacle");
                if (n.contains("rack") || n.contains("shelf")) {
                    gc.setFill(ENTITY_RACK_COLOR);
                } else if (n.contains("station") || n.contains("charge") || n.contains("depot")
                        || n.contains("pickup") || n.contains("delivery")) {
                    gc.setFill(ENTITY_STATION_COLOR);
                } else if (isWall) {
                    gc.setFill(ENTITY_OBSTACLE_COLOR);
                } else {
                    gc.setFill(ENTITY_FALLBACK_COLOR);
                }
                if (isWall) gc.fillRect(sx, sy, tileSize, tileSize);
                else        gc.fillRect(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            }

            if (zoom >= 0.8 && tileSize >= 18) {
                String lbl = entity.getName().length() > 5
                        ? entity.getName().substring(0, 4) + "\u2026"
                        : entity.getName();
                gc.setFill(ENTITY_LABEL_COLOR);
                gc.setFont(Font.font(Math.max(7.0, tileSize * 0.26)));
                gc.fillText(lbl, sx + pad + 1, sy + tileSize - pad - 2);
            }
        }
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

        // Draw icon using IconLoader utility
        javafx.scene.image.Image img = IconLoader.getIcon(obj.type);
        if (img != null && !img.isError()) {
            gc.drawImage(img, px, py, ow, oh);
        } else {
            gc.setFill(Color.web(objectBodyColor(obj.type)));
            gc.fillRect(px, py, ow, oh);
        }

        // Selection highlight border
        if (obj.selected) {
            gc.setStroke(OBJECT_SELECTION_COLOR);
            gc.setLineWidth(2.0);
            gc.strokeRect(px + 1, py + 1, ow - 2, oh - 2);
        }
    }

    /** Resolves the best-matching icon for an engine-loaded map entity. */
    private javafx.scene.image.Image resolveEntityIcon(MapEntity entity) {
        List<String> candidates = new ArrayList<>();

        if (entity instanceof Robot) {
            addIconCandidate(candidates, "ROBOT");
        }
        if (entity instanceof ChargingStation) {
            addIconCandidate(candidates, "CHARGER");
        }
        if (entity instanceof DeliveryStation) {
            addIconCandidate(candidates, "STATION");
            addIconCandidate(candidates, "DOCK");
            addIconCandidate(candidates, "DELIVERY");
        }
        if (entity instanceof Station) {
            addIconCandidate(candidates, "STATION");
        }
        if (entity instanceof Rack) {
            addIconCandidate(candidates, "SHELF");
            addIconCandidate(candidates, "RACK");
        }
        if (entity instanceof Obstacle) {
            addIconCandidate(candidates, "WALL");
            addIconCandidate(candidates, "OBSTACLE");
        }

        String name = entity.getName() == null ? "" : entity.getName().toLowerCase();
        if (name.contains("robot")) addIconCandidate(candidates, "ROBOT");
        if (name.contains("charge") || name.contains("charger")) addIconCandidate(candidates, "CHARGER");
        if (name.contains("station") || name.contains("pickup")) addIconCandidate(candidates, "STATION");
        if (name.contains("dock") || name.contains("depot") || name.contains("delivery")) addIconCandidate(candidates, "DOCK");
        if (name.contains("rack") || name.contains("shelf")) addIconCandidate(candidates, "SHELF");
        if (name.contains("wall") || name.contains("obstacle")) addIconCandidate(candidates, "WALL");

        for (String candidate : candidates) {
            javafx.scene.image.Image img = IconLoader.getIcon(candidate);
            if (img != null && !img.isError()) {
                return img;
            }
        }
        return null;
    }

    private void addIconCandidate(List<String> candidates, String type) {
        if (!candidates.contains(type)) {
            candidates.add(type);
        }
    }

    private boolean isWallLikeEntity(MapEntity entity) {
        if (entity instanceof Obstacle) {
            return true;
        }
        String name = entity.getName() == null ? "" : entity.getName().toLowerCase();
        return name.contains("wall") || name.contains("obstacle");
    }

    /** Returns the fill colour for each object type. */
    private String objectBodyColor(String type) {
        return switch (type) {
            case "ROBOT"   -> "#4D4B45";
            case "CHARGER" -> "#706E65";
            case "STATION" -> "#599068";
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

        if (source.getGraphic() != null) {
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            db.setDragView(source.getGraphic().snapshot(params, null), e.getX(), e.getY());
        }

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
            // Left click: selection only
            CanvasObject hit = objectAtScreenPos(e.getX(), e.getY());
            if (hit != null) {
                selectObject(hit);
                draggingOnCanvas = hit;
            } else {
                selectObject(null);
                draggingOnCanvas = null;
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            // Right click: pan mode
            draggingOnCanvas = null;
        }
    }

    private void onViewportMouseDragged(MouseEvent e) {
        double dx = e.getX() - lastMouseX;
        double dy = e.getY() - lastMouseY;

        if (draggingOnCanvas != null && e.getButton() == MouseButton.PRIMARY) {
            // Left-drag: move selected object – snap to nearest tile
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
        } else if (e.getButton() == MouseButton.SECONDARY) {
            // Right-drag: pan the viewport
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
            if (viewportModeLabel   != null) viewportModeLabel.setText("right-click to pan, left-click to select");
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
            updateSelectionLabel();
        }
        drawViewport();
    }

    private void showPropertiesFor(CanvasObject obj) {
        if (propertiesPanel == null) return;
        propertiesPanel.getChildren().clear();

        // Title
        Label title = new Label(obj.name);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        propertiesPanel.getChildren().add(title);

        // Separator
        Separator sep = new Separator();
        propertiesPanel.getChildren().add(sep);

        // Type (read-only)
        HBox typeBox = new HBox(8);
        typeBox.getChildren().addAll(
                new Label("Type:"),
                new Label(obj.type) {{ setStyle("-fx-text-fill: #666;"); }}
        );
        propertiesPanel.getChildren().add(typeBox);

        // UUID (read-only, using name as unique ID for now)
        HBox uuidBox = new HBox(8);
        uuidBox.getChildren().addAll(
                new Label("UUID:"),
            new Label(obj.name) {{ setStyle("-fx-text-fill: #3D3C39; -fx-font-family: monospace;"); }}
        );
        propertiesPanel.getChildren().add(uuidBox);

        // Name (editable)
        HBox nameBox = new HBox(8);
        TextField nameField = new TextField(obj.name);
        nameField.setStyle("-fx-font-size: 11;");
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                obj.name = newVal;
                updateOutliner();
                drawViewport();
            }
        });
        nameBox.getChildren().addAll(new Label("Name:"), nameField);
        propertiesPanel.getChildren().add(nameBox);

        // Position (editable)
        HBox posBox = new HBox(8);
        posBox.setPrefHeight(30);
        Spinner<Integer> xSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, obj.tileX));
        Spinner<Integer> ySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, obj.tileY));
        xSpinner.setPrefWidth(60);
        ySpinner.setPrefWidth(60);
        xSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            obj.tileX = newVal;
            drawViewport();
        });
        ySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            obj.tileY = newVal;
            drawViewport();
        });
        posBox.getChildren().addAll(
                new Label("Position:"),
                new Label("X:"), xSpinner,
                new Label("Y:"), ySpinner
        );
        propertiesPanel.getChildren().add(posBox);

        // Type-specific properties (extensible)
        addTypeSpecificProperties(obj);
    }

    /** Add type-specific property editors. Designed to be easily extensible. */
    private void addTypeSpecificProperties(CanvasObject obj) {
        switch (obj.type.toUpperCase()) {
            case "ROBOT":
                Label robotProps = new Label("Robot configuration: [TODO]");
                propertiesPanel.getChildren().add(robotProps);
                break;
            case "RACK", "SHELF":
                Label rackProps = new Label("Rack configuration: [TODO]");
                propertiesPanel.getChildren().add(rackProps);
                break;
            case "CHARGER":
                Label chargerProps = new Label("Charger settings: [TODO]");
                propertiesPanel.getChildren().add(chargerProps);
                break;
            // Add more types as needed
        }
    }

    private void clearPropertiesPanel() {
        if (propertiesPanel == null) return;
        propertiesPanel.getChildren().clear();
        propertiesPanel.getChildren().add(
                new Label("Select an object."));
    }

    /** Updates the selection text below the viewport with a tip. */
    private void updateSelectionLabel() {
        if (viewportStatusLabel != null) {
            String tip = ViewportTips.getRandomSelectionTip();
            viewportStatusLabel.setText("select — " + tip);
        }
    }

    /** Starts the tip rotation timer, cycling a new tip every 5 seconds. */
    private void startTipRotation() {
        tipRotationLoop = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            if (tipLabel != null) {
                tipLabel.setText("TIP: " + ViewportTips.nextTip());
            }
        }));
        tipRotationLoop.setCycleCount(Animation.INDEFINITE);
        tipRotationLoop.play();
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
        if (idx < 0 || idx >= outlinerBacking.size()) return;

        Object selected = outlinerBacking.get(idx);
        if (selected instanceof CanvasObject canvasObject) {
            selectObject(canvasObject);
        }
    }

    private void filterOutliner(String query) {
        populateOutliner();
    }

    /** Rebuilds the outliner list from the current engine map. */
    private void populateOutliner() {
        if (outlinerListView == null || engine == null || engine.getMap() == null || engine.getMap().getEntities() == null) return;
        String filter = (outlinerSearchField != null && outlinerSearchField.getText() != null)
                ? outlinerSearchField.getText().toLowerCase() : "";
        outlinerListView.getItems().clear();
        outlinerBacking.clear();
        int count = 0;
        for (MapEntity e : engine.getMap().getEntities()) {
            String icon  = (e instanceof Robot) ? "\ud83e\udd16 "
                         : (e instanceof Rack)  ? "\ud83d\udce6 "
                         : (e instanceof Station) ? "\u26a1 "
                         : (e instanceof Obstacle) ? "\ud83e\uddf1 " : "\u25ab ";
            String entry = icon + e.getName() + "  " + e.getPosition();
            if (filter.isEmpty() || entry.toLowerCase().contains(filter)) {
                outlinerListView.getItems().add(entry);
                outlinerBacking.add(e);
                count++;
            }
        }
        if (objsLabel != null) objsLabel.setText("objs: " + count);
    }

    /** Refreshes the Outliner list and the objs: counter. */
    private void updateOutliner() {
        if (outlinerListView != null) {
            outlinerListView.getItems().clear();
            outlinerBacking.clear();
            for (CanvasObject obj : objects) {
                outlinerListView.getItems().add(obj.type + ":  " + obj.name);
                outlinerBacking.add(obj);
            }
        }
        if (objsLabel != null) objsLabel.setText("objs: " + outlinerBacking.size());
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
            SimulationEngine reloaded = new SimulationEngine(AppState.getConfigPath());
            if (reloaded == null || reloaded.getMap() == null || reloaded.getInitError() != null) {
                if (simStatusLabel != null) {
                    simStatusLabel.setText("ERROR");
                    simStatusLabel.setStyle("-fx-text-fill: #D6453D; -fx-font-weight: bold;");
                }
                if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
                if (simProgressBar != null) simProgressBar.setProgress(0);
                log("\u26a0 Simulation reset failed: "
                        + (reloaded != null && reloaded.getInitError() != null ? reloaded.getInitError() : "unknown error"));
                return;
            }
            engine = reloaded;
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

    @FXML private void onSpeed1() { setSpeed(1); log("Speed set to ×1."); }
    @FXML private void onSpeed2() { setSpeed(2); log("Speed set to ×2."); }
    @FXML private void onSpeed3() { setSpeed(3); log("Speed set to ×3."); }

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
    //  Viewport zoom buttons
    // ------------------------------------------------------------------ //

    @FXML private void onZoomIn()  { zoom = Math.min(zoom * 1.2, 10.0); drawViewport(); }
    @FXML private void onZoomOut() { zoom = Math.max(zoom / 1.2, 0.2);  drawViewport(); }
    
    @FXML
    private void onReturnToOrigin() {
        viewOffsetX = 0;
        viewOffsetY = 0;
        zoom = 1.0;
        drawViewport();
        log("Viewport reset to origin.");
    }

    // ------------------------------------------------------------------ //
    //  Console
    // ------------------------------------------------------------------ //

    @FXML
    private void onClearConsole() {
        if (consoleArea != null) consoleArea.clear();
    }

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

