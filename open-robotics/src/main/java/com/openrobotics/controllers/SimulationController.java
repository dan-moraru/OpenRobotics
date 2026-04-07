package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.map.entities.station.Station;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
public class SimulationController implements ScreenNavigator.Cleanable {

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
    @FXML private CheckMenuItem toggleSidebarItem;
    @FXML private CheckMenuItem toggleConsoleItem;
    @FXML private SplitPane     mainSplitPane;
    @FXML private VBox sidebarPanel;
    @FXML private VBox consoleShell;
    @FXML private Label     tickDisplayLabel;

    // ── PROPERTIES PANEL ────────────────────────────────────────────────
    @FXML private VBox propertiesPanel;
    @FXML private Label propDescLabel;
    @FXML private TextField strPropField;

    // ── CONSOLE ─────────────────────────────────────────────────────────
    @FXML private TextArea consoleArea;

    // ── TAB STRIP ─────────────────────────────────────────────────────────
    @FXML private Button editorTabBtn;
    @FXML private Button resultsTabBtn;

    // ── PLAYBACK ────────────────────────────────────────────────────────
    @FXML private Button      speed1Btn;
    @FXML private Button      speed2Btn;
    @FXML private Button      speed3Btn;
    @FXML private ProgressBar simProgressBar;

    // ── Viewport navigation state ─────────────────────────────────────────
    private double lastMouseX;
    private double lastMouseY;
    private double viewportMouseX = -1;  // last known mouse X over viewport (-1 = unset)
    private double viewportMouseY = -1;
    private double viewOffsetX = 0;
    private double viewOffsetY = 0;
    private double zoom        = 1.0;

    // ── Canvas logical size (tiles) – read from AppState on init ──────────
    private int     canvasWidthTiles;
    private int     canvasHeightTiles;
    // Tile offset applied to all entity/object rendering so they are centred in the border
    private int     entityOffsetTileX = 0;
    private int     entityOffsetTileY = 0;
    private boolean viewportCentered  = false;  // ensures we only auto-centre once

    // ── Simulation state ──────────────────────────────────────────────────
    private boolean running = false;
    private boolean paused  = false;

    // ── Engine binding ────────────────────────────────────────────────────
    private SimulationEngine engine;
    private Timeline         simLoop;
    private double           speedFactor  = 1.0;
    private double baseTickMs = 100.0;
    private static final Color VIEWPORT_BG_COLOR = Color.web("#CDCBC3");
    private static final Color VIEWPORT_BG_OUTSIDE_COLOR = Color.web("#A8A598");
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
    private static final Color ENTITY_LABEL_COLOR = Color.web("#1a1a18");
    private static final Color OBJECT_SELECTION_COLOR = Color.web("#C2BEAE");
    // ── Object rendering ──────────────────────────────────────────────────
    // Uses IconLoader utility for icon caching

    // ── Canvas object state ───────────────────────────────────────────────

    private final List<Object> outlinerBacking = new ArrayList<>();
    private MapEntity selectedEntity = null;
    private MapEntity draggingOnCanvas = null;
    private int nextObjId = 1;
    private Timeline tipRotationLoop;

    // Animation state
    private boolean animating = false;
    private double animationProgress = 0.0;
    private Timeline animTimeline = null;
    private Map<java.util.UUID, com.openrobotics.map.Vector2D> prevRobotPositions = new HashMap<>();
    private static final int ANIMATION_FRAMES = 5;

    // ------------------------------------------------------------------ //
    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Read canvas size from shared state (set in Setup screen)
        canvasWidthTiles  = AppState.getCanvasWidthTiles();
        canvasHeightTiles = AppState.getCanvasHeightTiles();

        // Bind canvas size to the viewport stack so it fills the pane
        warehouseCanvas.widthProperty().bind(viewportStack.widthProperty());
        warehouseCanvas.heightProperty().bind(viewportStack.heightProperty());

        // Redraw whenever size changes; centre canvas on first layout
        warehouseCanvas.widthProperty().addListener((obs, oldW, newW) -> {
            if (!viewportCentered && newW.doubleValue() > 0 && warehouseCanvas.getHeight() > 0) {
                centerViewportOnCanvas();
                viewportCentered = true;
            }
            drawViewport();
        });
        warehouseCanvas.heightProperty().addListener((obs, oldH, newH) -> {
            if (!viewportCentered && warehouseCanvas.getWidth() > 0 && newH.doubleValue() > 0) {
                centerViewportOnCanvas();
                viewportCentered = true;
            }
            drawViewport();
        });

        // Viewport mouse interactions
        viewportStack.setOnMousePressed(this::onViewportMousePressed);
        viewportStack.setOnMouseDragged(this::onViewportMouseDragged);
        viewportStack.setOnMouseReleased(this::onViewportMouseReleased);
        viewportStack.setOnMouseMoved(e -> { viewportMouseX = e.getX(); viewportMouseY = e.getY(); });
        viewportStack.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            double mouseX = e.getX();
            double mouseY = e.getY();
            double oldZoom = zoom;
            zoom = Math.max(0.2, Math.min(zoom * factor, 10.0));
            double zoomRatio = zoom / oldZoom;
            viewOffsetX = mouseX - zoomRatio * (mouseX - viewOffsetX);
            viewOffsetY = mouseY - zoomRatio * (mouseY - viewOffsetY);
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

            // Compute canvas to tightly fit the loaded entities, then centre them inside.
            // The user's configured size (from SetupScreen) sets a minimum — the canvas
            // never shrinks below that, but entities are always centred within whatever size results.
            if (!loadedMap.getEntities().isEmpty()) {
                int maxTileX = 0, maxTileY = 0;
                for (MapEntity entity : loadedMap.getEntities()) {
                    maxTileX = Math.max(maxTileX, (int) entity.getPosition().getX());
                    maxTileY = Math.max(maxTileY, (int) entity.getPosition().getY());
                }
                int entitySpanX = maxTileX + 1;   // number of tiles the map occupies
                int entitySpanY = maxTileY + 1;
                // Default canvas = entity span + 2 tiles of padding (1 each side)
                canvasWidthTiles  = Math.max(AppState.getCanvasWidthTiles(),  entitySpanX + 2);
                canvasHeightTiles = Math.max(AppState.getCanvasHeightTiles(), entitySpanY + 2);
                // Offset so entities are centred inside the border
                entityOffsetTileX = (canvasWidthTiles  - entitySpanX) / 2;
                entityOffsetTileY = (canvasHeightTiles - entitySpanY) / 2;
            }
            if (canvasSizeLabel != null)
                canvasSizeLabel.setText("Canvas Size: " + canvasWidthTiles + "×" + canvasHeightTiles + " Tiles");

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

        if (engine != null && tickDisplayLabel != null) {
            tickDisplayLabel.setText("TICK " + engine.getTickCounter());
        }
        if (engine != null && simProgressBar != null) {
            simProgressBar.setProgress(Math.min(1.0,
                engine.getTickCounter() / (double) Math.max(1, engine.getMaxTicks())));
        }

        // Pre-load icons and initialize tips
        IconLoader.preloadAllIcons();
        updateSelectionLabel();
        startTipRotation();

        if (editModeLabel    != null) editModeLabel.setText("Edit mode");
        if (viewportModeLabel != null) viewportModeLabel.setText("Right-click to pan, left-click to select");
        if (tipLabel != null) tipLabel.setText("TIP: " + ViewportTips.nextTip());

        // Lock sidebar divider to 230px to prevent fractional-pixel drift on Windows DPI scaling.
        // Re-lock on every width change so layout passes from label/outliner updates cannot drift it.
        if (mainSplitPane != null) {
            mainSplitPane.widthProperty().addListener((obs, oldW, newW) -> {
                if (newW.doubleValue() > 0) {
                    mainSplitPane.setDividerPosition(0, 230.0 / newW.doubleValue());
                }
            });
            if (mainSplitPane.getWidth() > 0) {
                mainSplitPane.setDividerPosition(0, 230.0 / mainSplitPane.getWidth());
            }
        }

        log("Simulation screen ready. Drag an object from the panel into the viewport.");
    }

    // ------------------------------------------------------------------ //
    //  Viewport rendering
    // ------------------------------------------------------------------ //

    /** Centers the viewport so the canvas border is centred in the visible area. */
    private void centerViewportOnCanvas() {
        double tileSize = 32.0 * zoom;
        viewOffsetX = (warehouseCanvas.getWidth()  - canvasWidthTiles  * tileSize) / 2.0;
        viewOffsetY = (warehouseCanvas.getHeight() - canvasHeightTiles * tileSize) / 2.0;
    }

    /** Draws the warehouse grid and all placed objects on the canvas. */
    private void drawViewport() {
        GraphicsContext gc = warehouseCanvas.getGraphicsContext2D();
        double w = warehouseCanvas.getWidth();
        double h = warehouseCanvas.getHeight();

        // Background – darker outside the allocated map area
        gc.setFill(VIEWPORT_BG_OUTSIDE_COLOR);
        gc.fillRect(0, 0, w, h);

        double tileSize = 32 * zoom;

        // Canvas (map) boundary – fill lighter inside
        double bx = viewOffsetX;
        double by = viewOffsetY;
        double bw = canvasWidthTiles  * tileSize;
        double bh = canvasHeightTiles * tileSize;
        gc.setFill(VIEWPORT_BG_COLOR);
        gc.fillRect(bx, by, bw, bh);

        // Grid lines
        gc.setStroke(VIEWPORT_GRID_COLOR);
        gc.setLineWidth(0.5);
        for (double x = viewOffsetX % tileSize; x < w; x += tileSize)
            gc.strokeLine(x, 0, x, h);
        for (double y = viewOffsetY % tileSize; y < h; y += tileSize)
            gc.strokeLine(0, y, w, y);

        // Centre crosshair
        gc.setStroke(VIEWPORT_CROSSHAIR_COLOR);
        gc.setLineWidth(1.0);
        gc.strokeLine(w / 2 - 10, h / 2, w / 2 + 10, h / 2);
        gc.strokeLine(w / 2, h / 2 - 10, w / 2, h / 2 + 10);

        // Canvas boundary rectangle
        gc.setStroke(Color.web("#5D5B54"));
        gc.setLineWidth(2.0);
        gc.strokeRect(bx, by, bw, bh);

        drawEntities(gc);
    }

    /** Renders every entity from the engine onto the canvas. */
    private void drawEntities(GraphicsContext gc) {
        if (engine == null || engine.getMap() == null) return;
        double tileSize = 32 * zoom;
        double pad = Math.max(1.0, tileSize * 0.06);

        for (MapEntity entity : engine.getMap().getEntities()) {
            double ex = entity.getPosition().getX();
            double ey = entity.getPosition().getY();

            if (animating && entity instanceof Robot && prevRobotPositions.containsKey(entity.getId())) {
                com.openrobotics.map.Vector2D prev = prevRobotPositions.get(entity.getId());
                ex = prev.getX() + (ex - prev.getX()) * animationProgress;
                ey = prev.getY() + (ey - prev.getY()) * animationProgress;
            }

            double sx = viewOffsetX + (ex + entityOffsetTileX) * tileSize;
            double sy = viewOffsetY + (ey + entityOffsetTileY) * tileSize;

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
                double fontSize = Math.max(6.0, tileSize * 0.18);
                double maxLabelWidth = tileSize - 2 * pad - 2;
                int maxChars = Math.max(2, (int)(maxLabelWidth / (fontSize * 0.55)));
                String lbl = entity.getName().length() > maxChars
                        ? entity.getName().substring(0, maxChars - 1) + "\u2026"
                        : entity.getName();
                gc.setFill(ENTITY_LABEL_COLOR);
                gc.setFont(Font.font(fontSize));
                gc.fillText(lbl, sx + pad + 1, sy + tileSize - pad - 2, maxLabelWidth);
            }
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
    //  Map-bounds helpers (clamp positions to the engine map, not the canvas)
    // ------------------------------------------------------------------ //

    private int maxMapTileX() {
        if (engine != null && engine.getMap() != null) return engine.getMap().getWidth() - 1;
        return Math.max(0, canvasWidthTiles - entityOffsetTileX - 1);
    }

    private int maxMapTileY() {
        if (engine != null && engine.getMap() != null) return engine.getMap().getHeight() - 1;
        return Math.max(0, canvasHeightTiles - entityOffsetTileY - 1);
    }

    private int clampTileX(int tx) { return Math.max(0, Math.min(tx, maxMapTileX())); }
    private int clampTileY(int ty) { return Math.max(0, Math.min(ty, maxMapTileY())); }

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
            javafx.scene.image.WritableImage snap = source.getGraphic().snapshot(params, null);
            db.setDragView(snap, snap.getWidth() / 2, snap.getHeight() / 2);
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
                int tx = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
                int ty = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);
                viewportStatusLabel.setText(
                        "dragging(" + e.getDragboard().getString().toLowerCase()
                        + ")  →  (" + tx + ", " + ty + ")");
            }
        }
        e.consume();
    }

    /** Creates a new entity at the tile where the user dropped. */
    private void onCanvasDragDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (db.hasString()) {
            String type     = db.getString();
            double tileSize = 32 * zoom;
            int tx = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
            int ty = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);

            if (engine != null && engine.getMap() != null) {
                MapEntity entity = createEntityFromType(type, tx, ty, type.toLowerCase() + "_" + nextObjId++);
                if (entity != null) {
                    engine.addEntity(entity);
                    selectEntity(entity);
                }
            }

            populateOutliner();
            drawViewport();

            log("Added " + type + " at tile (" + tx + ", " + ty + ").");
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            e.setDropCompleted(true);
        }
        e.consume();
    }

    private MapEntity createEntityFromType(String type, int x, int y, String name) {
        java.util.UUID id = java.util.UUID.randomUUID();
        com.openrobotics.map.Vector2D pos = new com.openrobotics.map.Vector2D(x, y);
        String upperType = type.toUpperCase();
        if ("ROBOT".equals(upperType)) {
            Robot robot = new Robot(id, name, pos);
            robot.setNav(new GreedyNavigationStrategy(42L));
            robot.setSensor(new ProximitySensor());
            return robot;
        } else if ("CHARGER".equals(upperType) || "CHARGING".equals(upperType)) {
            return new ChargingStation(id, name, pos);
        } else if ("STATION".equals(upperType) || "DOCK".equals(upperType) || "DELIVERY".equals(upperType)) {
            return new DeliveryStation(id, name, pos);
        } else if ("RACK".equals(upperType) || "SHELF".equals(upperType)) {
            return new Rack(id, name, pos);
        } else if ("WALL".equals(upperType) || "OBSTACLE".equals(upperType)) {
            return new Obstacle(id, name, pos);
        }
        return new MapEntity(id, name, pos);
    }

    // ------------------------------------------------------------------ //
    //  Drag existing object WITHIN canvas  (mouse events)
    // ------------------------------------------------------------------ //

    private void onViewportMousePressed(MouseEvent e) {
        viewportStack.requestFocus();
        lastMouseX = e.getX();
        lastMouseY = e.getY();
        viewportMouseX = e.getX();
        viewportMouseY = e.getY();

        if (e.getButton() == MouseButton.PRIMARY) {
            // Left click: selection only
            MapEntity entityHit = entityAtScreenPos(e.getX(), e.getY());
            if (entityHit != null) {
                selectEntity(entityHit);
                draggingOnCanvas = null;
            } else {
                selectEntity(null);
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
            // Left-drag: move selected entity – snap to nearest tile, clamped to map bounds
            double tileSize = 32 * zoom;
            int newTX = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
            int newTY = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);
            draggingOnCanvas.setPosition(new com.openrobotics.map.Vector2D(newTX, newTY));
            if (viewportStatusLabel != null)
                viewportStatusLabel.setText(
                        "dragging(" + draggingOnCanvas.getName()
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
            log("Moved " + draggingOnCanvas.getName()
                + " to tile (" + (int)draggingOnCanvas.getPosition().getX() + ", " + (int)draggingOnCanvas.getPosition().getY() + ").");
            draggingOnCanvas = null;
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            if (viewportModeLabel   != null) viewportModeLabel.setText("right-click to pan, left-click to select");
        }
    }

    // ------------------------------------------------------------------ //
    //  Hit-testing
    // ------------------------------------------------------------------ //

    /** Returns the topmost engine entity whose rendered area contains (sx, sy), or null. */
    private MapEntity entityAtScreenPos(double sx, double sy) {
        if (engine == null || engine.getMap() == null) return null;
        double tileSize = 32 * zoom;
        List<MapEntity> entities = engine.getMap().getEntities();
        for (int i = entities.size() - 1; i >= 0; i--) {
            MapEntity entity = entities.get(i);
            double px = (entity.getPosition().getX() + entityOffsetTileX) * tileSize + viewOffsetX;
            double py = (entity.getPosition().getY() + entityOffsetTileY) * tileSize + viewOffsetY;
            if (sx >= px && sx < px + tileSize && sy >= py && sy < py + tileSize)
                return entity;
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Selection & Properties panel
    // ------------------------------------------------------------------ //

    private void deleteSelected() {
        if (selectedEntity == null) return;
        log("Deleted " + selectedEntity.getName() + ".");
        if (engine != null) {
            engine.removeEntity(selectedEntity);
        }
        selectedEntity = null;
        populateOutliner();
        drawViewport();
    }

    private void copySelected() {
        if (selectedEntity == null) return;
        clipboardEntity = selectedEntity;
        log("Copied " + clipboardEntity.getName() + ".");
    }

    private void pasteClipboard() {
        if (clipboardEntity == null || engine == null || engine.getMap() == null) return;
        int newX = clampTileX((int)clipboardEntity.getPosition().getX() + 1);
        int newY = clampTileY((int)clipboardEntity.getPosition().getY() + 1);
        MapEntity copy = createEntityFromType(
            clipboardEntity instanceof Robot ? "ROBOT" :
            clipboardEntity instanceof ChargingStation ? "CHARGER" :
            clipboardEntity instanceof DeliveryStation ? "STATION" :
            clipboardEntity instanceof Rack ? "RACK" : "OBSTACLE",
            newX, newY, clipboardEntity.getName() + "_copy");
        engine.addEntity(copy);
        selectEntity(copy);
        populateOutliner();
        drawViewport();
        log("Pasted " + copy.getName() + " at tile (" + newX + ", " + newY + ").");
    }

    private MapEntity clipboardEntity = null;

    private void selectEntity(MapEntity entity) {
        selectedEntity = entity;

        if (entity != null) {
            if (engine != null && engine.getMap() != null) {
                int idx = engine.getMap().getEntities().indexOf(entity);
                if (outlinerListView != null && idx >= 0)
                    outlinerListView.getSelectionModel().select(idx);
            }
            showPropertiesFor(entity);
        } else {
            if (outlinerListView != null)
                outlinerListView.getSelectionModel().clearSelection();
            clearPropertiesPanel();
            updateSelectionLabel();
        }
        drawViewport();
    }

    private void showPropertiesFor(MapEntity entity) {
        if (propertiesPanel == null) return;
        propertiesPanel.getChildren().clear();

        String typeName = entity.getClass().getSimpleName();

        Label title = new Label(entity.getName());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        propertiesPanel.getChildren().add(title);

        Separator sep = new Separator();
        propertiesPanel.getChildren().add(sep);

        HBox typeBox = new HBox(8);
        typeBox.getChildren().addAll(
                new Label("Type:"),
                new Label(typeName) {{ setStyle("-fx-text-fill: #666;"); }}
        );
        propertiesPanel.getChildren().add(typeBox);

        HBox uuidBox = new HBox(8);
        uuidBox.getChildren().addAll(
                new Label("UUID:"),
            new Label(entity.getId().toString()) {{ setStyle("-fx-text-fill: #3D3C39; -fx-font-family: monospace;"); }}
        );
        propertiesPanel.getChildren().add(uuidBox);

        HBox nameBox = new HBox(8);
        TextField nameField = new TextField(entity.getName());
        nameField.setStyle("-fx-font-size: 11;");
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                entity.setName(newVal);
                populateOutliner();
                drawViewport();
            }
        });
        nameBox.getChildren().addAll(new Label("Name:"), nameField);
        propertiesPanel.getChildren().add(nameBox);

        HBox posBox = new HBox(8);
        posBox.setPrefHeight(30);
        int maxTX = maxMapTileX();
        int maxTY = maxMapTileY();
        int posX = (int) entity.getPosition().getX();
        int posY = (int) entity.getPosition().getY();
        Spinner<Integer> xSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxTX, posX));
        Spinner<Integer> ySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxTY, posY));
        xSpinner.setPrefWidth(60);
        ySpinner.setPrefWidth(60);
        xSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            entity.setPosition(new com.openrobotics.map.Vector2D(newVal, (int)entity.getPosition().getY()));
            drawViewport();
        });
        ySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            entity.setPosition(new com.openrobotics.map.Vector2D((int)entity.getPosition().getX(), newVal));
            drawViewport();
        });
        posBox.getChildren().addAll(
                new Label("Position:"),
                new Label("X:"), xSpinner,
                new Label("Y:"), ySpinner
        );
        propertiesPanel.getChildren().add(posBox);

        if (entity instanceof Robot robot) {
            Label robotProps = new Label("Battery: " + robot.getBattery() + " | State: " + robot.getState());
            propertiesPanel.getChildren().add(robotProps);
        } else if (entity instanceof Station) {
            Label stationProps = new Label("Station configuration");
            propertiesPanel.getChildren().add(stationProps);
        } else if (entity instanceof Rack) {
            Label rackProps = new Label("Rack configuration");
            propertiesPanel.getChildren().add(rackProps);
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
            viewportStatusLabel.setText("Select — " + tip);
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
        if (selected instanceof MapEntity mapEntity) {
            selectEntity(mapEntity);
        }
    }

    private void filterOutliner(@SuppressWarnings("unused") String query) {
        // query is consumed by populateOutliner() via outlinerSearchField.getText()
        populateOutliner();
    }

    /** Rebuilds the outliner list from the current engine map.
     *  Compares new entries against current list to avoid unnecessary layout invalidation. */
    private void populateOutliner() {
        if (outlinerListView == null || engine == null || engine.getMap() == null || engine.getMap().getEntities() == null) return;
        String filter = (outlinerSearchField != null && outlinerSearchField.getText() != null)
                ? outlinerSearchField.getText().toLowerCase() : "";
        List<String> newItems = new ArrayList<>();
        List<Object> newBacking = new ArrayList<>();
        for (MapEntity e : engine.getMap().getEntities()) {
            String icon  = (e instanceof Robot) ? "\ud83e\udd16 "
                         : (e instanceof Rack)  ? "\ud83d\udce6 "
                         : (e instanceof Station) ? "\u26a1 "
                         : (e instanceof Obstacle) ? "\ud83e\uddf1 " : "\u25ab ";
            String entry = icon + e.getName() + "  " + e.getPosition();
            if (filter.isEmpty() || entry.toLowerCase().contains(filter)) {
                newItems.add(entry);
                newBacking.add(e);
            }
        }

        if (engine.getDispatcher() != null) {
            for (Task task : engine.getDispatcher().getAllTasks()) {
                String icon = "\u2b07 ";
                String entry = icon + "Task #" + task.getId() + " " + task.getStatus()
                    + " (" + task.getPickupLocation() + " → " + task.getDropoffLocation() + ")";
                if (filter.isEmpty() || entry.toLowerCase().contains(filter)) {
                    newItems.add(entry);
                    newBacking.add(task);
                }
            }
        }

        // Only update the ListView if content actually changed to avoid layout thrashing
        if (!newItems.equals(outlinerListView.getItems())) {
            outlinerListView.getItems().setAll(newItems);
            outlinerBacking.clear();
            outlinerBacking.addAll(newBacking);
        }

        String newText = "Objects: " + newItems.size();
        if (objsLabel != null && !newText.equals(objsLabel.getText())) {
            objsLabel.setText(newText);
        }
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
        if (animTimeline != null) { animTimeline.stop(); animTimeline = null; }
        animating = false;
        animationProgress = 0.0;
        prevRobotPositions.clear();
        selectedEntity = null;
        onStop();
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
        } else {
            // TODO: make sure template map resets here
        }
        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK " + (engine != null ? engine.getTickCounter() : 0));
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
        log("Step \u2192 TICK " + engine.getTickCounter());
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
        if (engine != null) baseTickMs = engine.getTickMs();
        simLoop = new Timeline(new KeyFrame(
                Duration.millis(baseTickMs / speedFactor),
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

    /** Stops all timelines and unbinds canvas properties. Called by ScreenNavigator before replacing this screen. */
    @Override
    public void cleanup() {
        stopLoop();
        if (tipRotationLoop != null) { tipRotationLoop.stop(); tipRotationLoop = null; }
        if (animTimeline    != null) { animTimeline.stop();    animTimeline    = null; }
        warehouseCanvas.widthProperty().unbind();
        warehouseCanvas.heightProperty().unbind();
        animating = false;
    }

    private void doTick() {
        if (engine == null) return;
        if (animating) return;

        if (engine.getRobots() != null) {
            prevRobotPositions.clear();
            for (Robot robot : engine.getRobots()) {
                prevRobotPositions.put(robot.getId(), new com.openrobotics.map.Vector2D(
                    robot.getPosition().getX(), robot.getPosition().getY()));
            }
        }

        if (!engine.tick()) {
            handleSimulationComplete();
            return;
        }
        populateOutliner();

        if (engine.getRobots() != null && engine.getRobots().length > 0) {
            startAnimation();
        } else {
            drawViewport();
        }

        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK " + engine.getTickCounter());
        if (simProgressBar != null) simProgressBar.setProgress(Math.min(1.0,
            engine.getTickCounter() / (double) Math.max(1, engine.getMaxTicks())));
    }

    private void handleSimulationComplete() {
        stopLoop();
        running = false;
        paused = false;
        if (simStatusLabel != null) {
            simStatusLabel.setText("COMPLETE");
            simStatusLabel.setStyle("-fx-text-fill: #599068; -fx-font-weight: bold;");
        }
        if (viewportStatusLabel != null) {
            viewportStatusLabel.setText("Workload complete");
        }
        log("Simulation complete at TICK " + engine.getTickCounter() + ".");
    }

    private void startAnimation() {
        if (animTimeline != null) animTimeline.stop();
        animating = true;
        animationProgress = 0.0;
        animTimeline = new Timeline();
        final int totalFrames = ANIMATION_FRAMES;
        for (int i = 0; i < totalFrames; i++) {
            final int frame = i;
            animTimeline.getKeyFrames().add(new KeyFrame(
                Duration.millis((frame + 1) * baseTickMs / speedFactor / totalFrames),
                e -> {
                    animationProgress = (double)(frame + 1) / totalFrames;
                    drawViewport();
                }
            ));
        }
        animTimeline.setOnFinished(e -> {
            animating = false;
            animationProgress = 0.0;
            prevRobotPositions.clear();
            drawViewport();
        });
        animTimeline.play();
    }

    // ------------------------------------------------------------------ //
    //  Viewport zoom buttons
    // ------------------------------------------------------------------ //

    @FXML private void onZoomIn() {
        double px = viewportMouseX >= 0 ? viewportMouseX : warehouseCanvas.getWidth()  / 2;
        double py = viewportMouseY >= 0 ? viewportMouseY : warehouseCanvas.getHeight() / 2;
        double oldZoom = zoom;
        zoom = Math.min(zoom * 1.2, 10.0);
        double r = zoom / oldZoom;
        viewOffsetX = px - r * (px - viewOffsetX);
        viewOffsetY = py - r * (py - viewOffsetY);
        drawViewport();
    }

    @FXML private void onZoomOut() {
        double px = viewportMouseX >= 0 ? viewportMouseX : warehouseCanvas.getWidth()  / 2;
        double py = viewportMouseY >= 0 ? viewportMouseY : warehouseCanvas.getHeight() / 2;
        double oldZoom = zoom;
        zoom = Math.max(zoom / 1.2, 0.2);
        double r = zoom / oldZoom;
        viewOffsetX = px - r * (px - viewOffsetX);
        viewOffsetY = py - r * (py - viewOffsetY);
        drawViewport();
    }

    @FXML
    private void onReturnToOrigin() {
        zoom = 1.0;
        centerViewportOnCanvas();
        drawViewport();
        log("Viewport reset to origin.");
    }

    @FXML
    private void onToggleSidebar() {
        if (sidebarPanel != null) {
            sidebarPanel.setVisible(toggleSidebarItem.isSelected());
            sidebarPanel.setManaged(toggleSidebarItem.isSelected());
        }
    }

    @FXML
    private void onToggleConsole() {
        if (consoleShell != null) {
            consoleShell.setVisible(toggleConsoleItem.isSelected());
            consoleShell.setManaged(toggleConsoleItem.isSelected());
        }
    }

    // ------------------------------------------------------------------ //
    //  Console
    // ------------------------------------------------------------------ //

    @FXML
    private void onClearConsole() {
        if (consoleArea != null) consoleArea.clear();
    }

    @FXML
    private void onSaveConfig() {
        ScreenNavigator.openDialog(ScreenNavigator.DIALOG_SAVE_CONFIG, "Save Configuration");
    }

    private void log(String message) {
        System.out.println("[SimulationController] " + message);
        if (consoleArea != null) consoleArea.appendText(message + "\n");
    }

    // ------------------------------------------------------------------ //
    //  Navigation & Menu
    // ------------------------------------------------------------------ //

    @FXML private void onTabEditor() {
        if (editorTabBtn  != null) { editorTabBtn.getStyleClass().setAll("tab-btn-active");  }
        if (resultsTabBtn != null) { resultsTabBtn.getStyleClass().setAll("tab-btn"); }
        /* already on editor tab – no navigation needed */
    }

    @FXML
    private void onBackToSetup() {
        stopLoop();
        running = false;
        paused = false;
        AppState.clear();
        ScreenNavigator.goToSetup();
    }

    @FXML
    private void onMenuWelcome() { ScreenNavigator.goToWelcome(); }

    @FXML
    private void onMenuCalculateResults() { ScreenNavigator.goToResults(); }

    @FXML
    private void onMenuGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/dan-moraru/OpenRobotics"));
        } catch (Exception ex) { log("Could not open browser."); }
    }

    @FXML
    private void onViewResults() {
        if (editorTabBtn  != null) { editorTabBtn.getStyleClass().setAll("tab-btn");  }
        if (resultsTabBtn != null) { resultsTabBtn.getStyleClass().setAll("tab-btn-active"); }
        ScreenNavigator.goToResults();
    }

    @FXML
    private void onExit() {
        if (ScreenNavigator.confirmExit()) javafx.application.Platform.exit();
    }
}

