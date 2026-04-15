package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.db.dao.MapDao;
import com.openrobotics.db.dao.SimLogDao;
import com.openrobotics.db.model.MapRecord;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.db.recordbuilders.MapRecordBuilder;
import com.openrobotics.db.recordbuilders.SimulationRunRecordBuilder;
import com.openrobotics.db.recordbuilders.WorkloadTaskRecordBuilder;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.eventtypes.SimulationRunEvent;
import com.openrobotics.logging.eventtypes.TaskEvent;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.map.entities.station.Station;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.simulationcore.SimulationError;
import com.openrobotics.task.Task;
import com.openrobotics.util.IconLoader;
import com.openrobotics.util.ScreenNavigator;
import com.openrobotics.util.ViewportTips;
import javafx.application.Platform;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
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

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @FXML private Button           intersectionObjectTile;

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
    @FXML private SplitPane     viewportConsoleSplit;
    @FXML private VBox sidebarPanel;
    @FXML private VBox consoleShell;
    @FXML private Label     tickDisplayLabel;
    @FXML private VBox      shortcutOverlay;

    // Saved divider positions so collapse/expand is smooth
    private double savedSidebarDivider  = 0.17;
    private double savedConsoleDivider  = 0.83;

    // ── PROPERTIES PANEL ────────────────────────────────────────────────
    @FXML private VBox propertiesPanel;
    @FXML private Label propDescLabel;
    @FXML private TextField strPropField;

    // ── CONSOLE ─────────────────────────────────────────────────────────
    @FXML private TextArea consoleArea;
    @FXML private Button    clearConsoleButton;

    // - DATABASE LOGGING
    @FXML private TextArea logArea;

    // ── TAB STRIP ─────────────────────────────────────────────────────────
    @FXML private Button editorTabBtn;
    @FXML private Button resultsTabBtn;

    // ── PLAYBACK ────────────────────────────────────────────────────────
    @FXML private Button      speed1Btn;
    @FXML private Button      speed2Btn;
    @FXML private Button      speed3Btn;
    @FXML private Button      speed10Btn;
    @FXML private Button      playBtn;
    @FXML private Button      pauseBtn;
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
    private boolean simulationFailed = false;
    // Snapshot of engine state at the moment play was first pressed (tick 0 baseline).
    // Restart always reloads from this, not from AppState.getConfigPath().
    private String initialSnapshotPath = null;

    // ── Engine binding ────────────────────────────────────────────────────
    private SimulationEngine engine;
    private Timeline         simLoop;
    private double           speedFactor  = 1.0;
    private int              localTick    = 0;
    private static final double BASE_TICK_MS = 100.0;
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
    private static final Color INTERSECTION_FILL_COLOR = Color.web("#8C7B38", 0.25);
    private static final Color INTERSECTION_STROKE_COLOR = Color.web("#6A4828");
    // ── Object rendering ──────────────────────────────────────────────────
    // Uses IconLoader utility for icon caching

    // ── Canvas object state ───────────────────────────────────────────────

    private final List<Object> outlinerBacking = new ArrayList<>();
    private MapEntity selectedEntity = null;
    private Rack pickingRack = null;
    private int  pickingSlot = -1;
    private Vector2D selectedIntersection = null;
    private MapEntity draggingOnCanvas = null;
    // Position of draggingOnCanvas at the moment the drag started — used to update tasks on release
    private com.openrobotics.map.Vector2D dragStartPosition = null;
    private int nextObjId = 1;
    private Timeline tipRotationLoop;

    // ── Drag-over tile highlight ──────────────────────────────────────────
    private int dragHighlightTileX = -1;
    private int dragHighlightTileY = -1;

    // Animation state
    private boolean animating = false;
    private double animationProgress = 0.0;
    private Timeline animTimeline = null;
    private Map<java.util.UUID, com.openrobotics.map.Vector2D> prevRobotPositions = new HashMap<>();
    private static final int ANIMATION_FRAMES = 5;

    // ── Undo / Redo ─────────────────────────────────────────────────────
    private final java.util.Deque<EditorAction> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<EditorAction> redoStack = new java.util.ArrayDeque<>();

    // Used for updating simulation logs
    private Timeline logPollingTimeline;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long lastSeenLogId = 0;

    /** Sealed interface for reversible editor actions. */
    private sealed interface EditorAction permits AddAction, DeleteAction, MoveAction, RenameAction, AlgorithmChangeAction, BatteryChangeAction, SensorChangeAction {
        void undo(SimulationController ctrl);
        void redo(SimulationController ctrl);
        String description();
    }
    private record AddAction(MapEntity entity) implements EditorAction {
        public void undo(SimulationController ctrl) {
            if (ctrl.engine != null) ctrl.engine.removeEntity(entity);
            if (ctrl.selectedEntity == entity) ctrl.selectedEntity = null;
            ctrl.populateOutliner(); ctrl.drawViewport();
        }
        public void redo(SimulationController ctrl) {
            if (ctrl.engine != null) ctrl.engine.addEntity(entity);
            ctrl.populateOutliner(); ctrl.drawViewport();
        }
        public String description() { return "Add " + entity.getName(); }
    }
    private record DeleteAction(MapEntity entity) implements EditorAction {
        public void undo(SimulationController ctrl) {
            if (ctrl.engine != null) ctrl.engine.addEntity(entity);
            ctrl.populateOutliner(); ctrl.drawViewport();
        }
        public void redo(SimulationController ctrl) {
            if (ctrl.engine != null) ctrl.engine.removeEntity(entity);
            if (ctrl.selectedEntity == entity) ctrl.selectedEntity = null;
            ctrl.populateOutliner(); ctrl.drawViewport();
        }
        public String description() { return "Delete " + entity.getName(); }
    }
    private record MoveAction(MapEntity entity, com.openrobotics.map.Vector2D from, com.openrobotics.map.Vector2D to) implements EditorAction {
        public void undo(SimulationController ctrl) { entity.setPosition(from); ctrl.drawViewport(); }
        public void redo(SimulationController ctrl) { entity.setPosition(to); ctrl.drawViewport(); }
        public String description() { return "Move " + entity.getName() + " from " + from + " to " + to; }
    }
    private record RenameAction(MapEntity entity, String oldName, String newName) implements EditorAction {
        public void undo(SimulationController ctrl) { entity.setName(oldName); ctrl.populateOutliner(); ctrl.drawViewport(); }
        public void redo(SimulationController ctrl) { entity.setName(newName); ctrl.populateOutliner(); ctrl.drawViewport(); }
        public String description() { return "Rename " + oldName + " → " + newName; }
    }
    private record AlgorithmChangeAction(Robot robot, String oldAlgo, String newAlgo, com.openrobotics.robot.navigation.NavigationStrategy oldNav, com.openrobotics.robot.navigation.NavigationStrategy newNav) implements EditorAction {
        public void undo(SimulationController ctrl) { robot.setNav(oldNav); ctrl.drawViewport(); }
        public void redo(SimulationController ctrl) { robot.setNav(newNav); ctrl.drawViewport(); }
        public String description() { return "Change " + robot.getName() + " algorithm " + oldAlgo + " → " + newAlgo; }
    }
    private record BatteryChangeAction(Robot robot, float oldBattery, float newBattery) implements EditorAction {
        public void undo(SimulationController ctrl) { robot.setBattery(oldBattery); ctrl.drawViewport(); }
        public void redo(SimulationController ctrl) { robot.setBattery(newBattery); ctrl.drawViewport(); }
        public String description() { return "Change " + robot.getName() + " battery " + oldBattery + " → " + newBattery; }
    }
    private record SensorChangeAction(Robot robot, String oldSensor, String newSensor, com.openrobotics.robot.sensors.SensorStrategy oldSensorStrat, com.openrobotics.robot.sensors.SensorStrategy newSensorStrat) implements EditorAction {
        public void undo(SimulationController ctrl) { robot.setSensor(oldSensorStrat); ctrl.drawViewport(); }
        public void redo(SimulationController ctrl) { robot.setSensor(newSensorStrat); ctrl.drawViewport(); }
        public String description() { return "Change " + robot.getName() + " sensor " + oldSensor + " → " + newSensor; }
    }

    private void pushAction(EditorAction action) {
        undoStack.push(action);
        redoStack.clear();
        saveEditorBaseline();
    }

    private void undoAction() {
        if (undoStack.isEmpty()) { log("Nothing to undo."); return; }
        EditorAction action = undoStack.pop();
        action.undo(this);
        redoStack.push(action);
        log("Undo: " + action.description());
        saveEditorBaseline();
    }

    private void redoAction() {
        if (redoStack.isEmpty()) { log("Nothing to redo."); return; }
        EditorAction action = redoStack.pop();
        action.redo(this);
        undoStack.push(action);
        log("Redo: " + action.description());
        saveEditorBaseline();
    }

    /** Returns true if editor mutations (add/move/delete/rename/property changes) are blocked. */
    private boolean isEditorLocked() {
        return running || localTick > 0 || simulationFailed;
    }

    /** Logs an error and returns true if the editor is locked. Use as a guard at the top of mutating methods. */
    private boolean guardEditor(String actionName) {
        if (isEditorLocked()) {
            log("\u26a0 Cannot " + actionName + " while the simulation is running, has advanced past tick 0, or has failed. Please reset first.");
            return true;
        }
        return false;
    }

    /** Saves the current editor state as the baseline for reset. Reuses a single temp file.
     *  Only snapshots when the simulation has not yet started (tick 0, not running), so that
     *  mid-run editor actions do not overwrite the clean baseline with a non-zero tick state. */
    private void saveEditorBaseline() {
        if (engine == null) return;
        if (running || engine.getTickCounter() > 0) return; // never overwrite with mid-run state
        try {
            if (initialSnapshotPath == null) {
                java.io.File snap = java.io.File.createTempFile("openrobotics_baseline_", ".json");
                snap.deleteOnExit();
                initialSnapshotPath = snap.getAbsolutePath();
            }
            engine.configSaving(initialSnapshotPath);
        } catch (Exception ex) {
            log("\u26a0 Could not snapshot initial state: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Set up periodic log polling (every 5 seconds) to fetch new logs from the database and update the logArea.
        logPollingTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> fetchLogsAsync())
        );
        logPollingTimeline.setCycleCount(Timeline.INDEFINITE);

        if (intersectionObjectTile != null) {
            intersectionObjectTile.managedProperty().bind(intersectionObjectTile.visibleProperty());
            intersectionObjectTile.setVisible(false);
        }

        // Read canvas size from shared state (set in Setup screen)
        canvasWidthTiles  = AppState.getCanvasWidthTiles();
        canvasHeightTiles = AppState.getCanvasHeightTiles();

        // Track viewport stack size with listeners (not bind) — same pattern as SetupController.
        // Using bind() makes the Canvas report its pixel size as its preferred size to the
        // layout engine, which then locks the SplitPane divider and prevents free dragging.
        // With managed="false" on the Canvas and listener-driven setWidth/setHeight, the
        // layout engine ignores the Canvas when computing preferred sizes, so dividers stay free.
        viewportStack.widthProperty().addListener((obs, oldW, newW) -> {
            warehouseCanvas.setWidth(newW.doubleValue());
            if (!viewportCentered && newW.doubleValue() > 0 && warehouseCanvas.getHeight() > 0) {
                centerViewportOnCanvas();
                viewportCentered = true;
            }
            drawViewport();
        });
        viewportStack.heightProperty().addListener((obs, oldH, newH) -> {
            warehouseCanvas.setHeight(newH.doubleValue());
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
            // Zoom toward the canvas center (where the crosshair is), not the mouse position
            double cx = warehouseCanvas.getWidth()  / 2;
            double cy = warehouseCanvas.getHeight() / 2;
            double oldZoom = zoom;
            zoom = Math.max(0.2, Math.min(zoom * factor, 10.0));
            double zoomRatio = zoom / oldZoom;
            viewOffsetX = cx - zoomRatio * (cx - viewOffsetX);
            viewOffsetY = cy - zoomRatio * (cy - viewOffsetY);
            drawViewport();
        });

        // Drop target: accept objects dragged from the Add Object sidebar
        viewportStack.setOnDragOver(this::onCanvasDragOver);
        viewportStack.setOnDragDropped(this::onCanvasDragDropped);
        viewportStack.setOnDragExited(e -> {
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            dragHighlightTileX = -1;
            dragHighlightTileY = -1;
            drawViewport();
        });

        // Keyboard shortcuts: Delete, Cmd+C, Cmd+V, Ctrl+Z, Ctrl+Y
        viewportStack.setFocusTraversable(true);
        viewportStack.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DELETE, BACK_SPACE -> deleteSelected();
                case C -> { if (e.isShortcutDown()) copySelected(); }
                case V -> { if (e.isShortcutDown()) pasteClipboard(); }
                case Z -> { if (e.isShortcutDown()) undoAction(); }
                case Y -> { if (e.isShortcutDown()) redoAction(); }
                default -> {}
            }
            e.consume();
        });

        // Outliner search filter
        if (outlinerSearchField != null) {
            outlinerSearchField.textProperty().addListener((obs, o, n) -> filterOutliner(n));
        }

        // Outliner selection listener -> update properties panel when user clicks an entity in the outliner
        if (outlinerListView != null) {
            outlinerListView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onOutlinerSelect());
        }

        // Bind engine from shared AppState
        engine = AppState.getEngine();
        if (engine == null && AppState.hasConfigPath()) {
            engine = new SimulationEngine(AppState.getConfigPath());
            if (engine == null || engine.getMap() == null) {
                String initErrorMsg = engine != null && engine.getInitError() != null
                        ? engine.getInitError()
                        : "constructor returned null";
                log("\u26a0 Config reload failed: " + initErrorMsg);
                if (viewportStatusLabel != null) {
                    viewportStatusLabel.setText("Load failed");
                }
                return;
            }
            AppState.setEngine(engine);
        }

        // Restore simulation tick from AppState (persists across tab switches)
        localTick = AppState.getSimulationTick();

        // Update RAM display
        updateRamLabel();

        if (engine != null && engine.getMap() != null) {
            com.openrobotics.map.Map loadedMap = engine.getMap();

            // Saving map to database.
            try {
                MapRecordBuilder recordBuilder = new MapRecordBuilder(loadedMap);
                MapRecord record = recordBuilder.build();
                MapDao.insert(record);
            } catch (Exception e) {
                System.out.println("Error saving map to database: " + e.getMessage());
            }

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

            refreshIntersectionObjectTileVisibility();
            populateOutliner();
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("Loaded " + loadedMap.getEntities().size() + " objects");
            }
            log("Loaded simulation with " + loadedMap.getEntities().size() + " entities. Press \u25b6 to start.");
            drawViewport();
        } else {
            refreshIntersectionObjectTileVisibility();
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("No config loaded");
            }
            log("No configuration loaded. Go to Setup \u2192 Load Config first.");
        }

        // Pre-load icons and initialize tips
        IconLoader.preloadAllIcons();
        // Keep "Loaded N objects" after a successful load; otherwise show the default selection tip
        // (replaces the transient "No config loaded" empty-state message).
        if (engine == null || engine.getMap() == null) {
            updateSelectionLabel();
        }
        startTipRotation();

        if (editModeLabel    != null) editModeLabel.setText("Edit mode");
        if (viewportModeLabel != null) viewportModeLabel.setText("Right-click to pan, left-click to select");
        if (tipLabel != null) tipLabel.setText("TIP: " + ViewportTips.nextTip());

        // Reset button colors to default CSS style (beige)
        if (playBtn  != null) playBtn.setStyle("");
        if (pauseBtn != null) pauseBtn.setStyle("");

        // Set initial sidebar position — not locked, user can drag freely
        if (mainSplitPane != null && mainSplitPane.getWidth() > 0) {
            mainSplitPane.setDividerPosition(0, 230.0 / mainSplitPane.getWidth());
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
        drawTrafficRuleIntersections(gc);

        // Highlight the tile the user is hovering over during a drag-from-sidebar
        if (dragHighlightTileX >= 0 && dragHighlightTileY >= 0) {
            double hx = viewOffsetX + (dragHighlightTileX + entityOffsetTileX) * tileSize;
            double hy = viewOffsetY + (dragHighlightTileY + entityOffsetTileY) * tileSize;
            gc.setFill(Color.web("#599068", 0.35));
            gc.fillRect(hx, hy, tileSize, tileSize);
            gc.setStroke(Color.web("#3a7a50"));
            gc.setLineWidth(2.0);
            gc.strokeRect(hx, hy, tileSize, tileSize);
        }
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

            // Green outline for the currently selected entity
            if (entity == selectedEntity) {
                gc.setStroke(Color.web("#1a743f"));
                gc.setLineWidth(Math.max(2.5, tileSize * 0.09));
                double inset = Math.max(1.5, tileSize * 0.04);
                gc.strokeRect(sx + inset, sy + inset, tileSize - 2 * inset, tileSize - 2 * inset);
            }

            if (pickingRack != null && entity instanceof DeliveryStation) {
                gc.setStroke(OBJECT_SELECTION_COLOR);
                gc.setLineWidth(Math.max(2.0, tileSize * 0.1));
                gc.strokeRect(sx + 1, sy + 1, tileSize - 2, tileSize - 2);
            }
        }
    }

    private void drawTrafficRuleIntersections(GraphicsContext gc) {
        if (engine == null || !engine.usesTrafficRulesPolicy()) return;
        double tileSize = 32 * zoom;
        double markerInset = Math.max(3.0, tileSize * 0.22);

        // Load intersection icon — falls back to primitive drawing if not found
        javafx.scene.image.Image intersectionIcon = IconLoader.getIcon("INTERSECTION");

        gc.setStroke(INTERSECTION_STROKE_COLOR);
        gc.setLineWidth(Math.max(1.5, tileSize * 0.08));

        for (Vector2D intersection : engine.getTrafficRuleIntersections()) {
            double sx = viewOffsetX + (intersection.getX() + entityOffsetTileX) * tileSize;
            double sy = viewOffsetY + (intersection.getY() + entityOffsetTileY) * tileSize;

            if (intersectionIcon != null && !intersectionIcon.isError()) {
                // Draw PNG icon
                gc.drawImage(intersectionIcon, sx, sy, tileSize, tileSize);
            } else {
                // Fallback: original primitive drawing
                double centerInset = Math.max(2.0, tileSize * 0.38);
                gc.setFill(INTERSECTION_FILL_COLOR);
                gc.fillOval(sx + markerInset, sy + markerInset, tileSize - 2 * markerInset, tileSize - 2 * markerInset);
                gc.strokeLine(sx + centerInset, sy + tileSize / 2.0, sx + tileSize - centerInset, sy + tileSize / 2.0);
                gc.strokeLine(sx + tileSize / 2.0, sy + centerInset, sx + tileSize / 2.0, sy + tileSize - centerInset);
            }

            // Selection highlight — always drawn regardless of icon
            if (intersection.equals(selectedIntersection)) {
                gc.setStroke(OBJECT_SELECTION_COLOR);
                gc.setLineWidth(Math.max(2.0, tileSize * 0.1));
                gc.strokeOval(
                        sx + Math.max(1.0, markerInset - 2.0),
                        sy + Math.max(1.0, markerInset - 2.0),
                        tileSize - 2 * Math.max(1.0, markerInset - 2.0),
                        tileSize - 2 * Math.max(1.0, markerInset - 2.0)
                );
                gc.setStroke(INTERSECTION_STROKE_COLOR);
                gc.setLineWidth(Math.max(1.5, tileSize * 0.08));
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
            case "INTERSECTION" -> "#8C7B38";
            default        -> "#5D5B54";
        };
    }

    private void refreshIntersectionObjectTileVisibility() {
        if (intersectionObjectTile != null) {
            intersectionObjectTile.setVisible(engine != null && engine.usesTrafficRulesPolicy());
        }
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

    private boolean isRobotEntity(MapEntity entity) {
        return entity instanceof Robot;
    }

    private boolean isStationOrDockEntity(MapEntity entity) {
        return entity instanceof ChargingStation || entity instanceof DeliveryStation;
    }

    private boolean isValidTileOccupancy(List<MapEntity> occupants) {
        int robotCount = 0;
        int stationDockCount = 0;
        int otherCount = 0;

        for (MapEntity entity : occupants) {
            if (isRobotEntity(entity)) {
                robotCount++;
            } else if (isStationOrDockEntity(entity)) {
                stationDockCount++;
            } else {
                otherCount++;
            }
        }

        if (robotCount > 1 || stationDockCount > 1) {
            return false;
        }

        // Any non station/dock non-robot entity must be alone on its tile.
        if (otherCount > 0) {
            return occupants.size() == 1;
        }

        // Valid: single robot, single station/dock, or one of each.
        return occupants.size() <= 2;
    }

    private boolean canPlaceEntityAt(MapEntity candidate, int x, int y, MapEntity ignoreEntity) {
        if (engine == null || engine.getMap() == null || candidate == null) {
            return false;
        }

        List<MapEntity> occupants = new ArrayList<>();
        for (MapEntity entity : engine.getMap().getEntities()) {
            if (entity == ignoreEntity) {
                continue;
            }
            if ((int) entity.getPosition().getX() == x && (int) entity.getPosition().getY() == y) {
                occupants.add(entity);
            }
        }

        occupants.add(candidate);
        return isValidTileOccupancy(occupants);
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
        if (source == null) {
            log("⚠ Drag ignored because the Add Object tile source was not available.");
            e.consume();
            return;
        }

        String type = source.getUserData() instanceof String s ? s : null;
        if (type == null || type.isBlank()) {
            log("⚠ Drag ignored because the Add Object tile is missing its type metadata.");
            e.consume();
            return;
        }

        if (running) {
            log("⚠ Drag ignored for " + type + " because the editor is locked. Stop or reset the simulation first.");
            e.consume();
            return;
        }

        Dragboard db = source.startDragAndDrop(TransferMode.COPY);
        ClipboardContent content = new ClipboardContent();
        content.putString(type);
        db.setContent(content);

        // Snapshot only the icon (ImageView) inside the button graphic — not the full
        // button including the description label.
        javafx.scene.Node graphic = source.getGraphic();
        javafx.scene.image.ImageView iconView = null;
        if (graphic instanceof javafx.scene.layout.HBox hbox) {
            for (javafx.scene.Node child : hbox.getChildren()) {
                if (child instanceof javafx.scene.image.ImageView iv) {
                    iconView = iv;
                    break;
                }
            }
        } else if (graphic instanceof javafx.scene.image.ImageView iv) {
            iconView = iv;
        }
        javafx.scene.Node snapTarget = iconView != null ? iconView : graphic;
        if (snapTarget != null) {
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);
            javafx.scene.image.WritableImage snap = snapTarget.snapshot(params, null);
            db.setDragView(snap, snap.getWidth() / 2, snap.getHeight() / 2);
        }

        if (viewportStatusLabel != null)
            viewportStatusLabel.setText("dragging(" + type.toLowerCase() + ")");

        e.consume();
    }

    /** Accept the drag as long as the dragboard carries an object-type string. */
    private void onCanvasDragOver(DragEvent e) {
        if (running) { e.consume(); return; }
        if (e.getDragboard().hasString()) {
            e.acceptTransferModes(TransferMode.COPY);
            double tileSize = 32 * zoom;
            int tx = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
            int ty = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);
            // Redraw only when the highlighted tile changes (avoids thrashing)
            if (tx != dragHighlightTileX || ty != dragHighlightTileY) {
                dragHighlightTileX = tx;
                dragHighlightTileY = ty;
                drawViewport();
            }
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText(
                        "dragging(" + e.getDragboard().getString().toLowerCase()
                                + ")  →  (" + tx + ", " + ty + ")");
            }
        }
        e.consume();
    }

    /** Creates a new entity at the tile where the user dropped. */
    private void onCanvasDragDropped(DragEvent e) {
        if (guardEditor("add objects")) { e.setDropCompleted(false); e.consume(); return; }
        Dragboard db = e.getDragboard();
        boolean dropCompleted = false;
        dragHighlightTileX = -1;
        dragHighlightTileY = -1;
        if (!db.hasString()) {
            log("⚠ Drop ignored because the dragged item did not carry an object type.");
            e.setDropCompleted(false);
            e.consume();
            return;
        }

        if (db.hasString()) {
            String type     = db.getString();
            double tileSize = 32 * zoom;
            int tx = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
            int ty = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);

            if (engine != null && engine.getMap() != null) {
                if ("INTERSECTION".equalsIgnoreCase(type)) {
                    dropCompleted = handleIntersectionDrop(tx, ty);
                    if (dropCompleted && viewportStatusLabel != null) viewportStatusLabel.setText("");
                    e.setDropCompleted(dropCompleted);
                    e.consume();
                    return;
                }

                MapEntity entity = createEntityFromType(type, tx, ty, type.toLowerCase() + "_" + nextObjId);
                if (entity != null) {
                    if (canPlaceEntityAt(entity, tx, ty, null)) {
                        engine.addEntity(entity);
                        nextObjId++;
                        selectEntity(entity);
                        populateOutliner();
                        drawViewport();
                        log("Added " + type + " at tile (" + tx + ", " + ty + ").");
                        if (viewportStatusLabel != null) viewportStatusLabel.setText("");
                        dropCompleted = true;
                        pushAction(new AddAction(entity));
                    } else {
                        log("Placement blocked at tile (" + tx + ", " + ty + "). Only Robot + ChargingStation/DeliveryStation can share a tile.");
                        if (viewportStatusLabel != null) {
                            viewportStatusLabel.setText("blocked at (" + tx + ", " + ty + ")");
                        }
                    }
                }
            } else {
                log("⚠ Drop ignored because no simulation map is loaded yet.");
            }
        }
        e.setDropCompleted(dropCompleted);
        e.consume();
    }

    private boolean handleIntersectionDrop(int tx, int ty) {
        if (engine == null || engine.getMap() == null) {
            return false;
        }
        if (!engine.usesTrafficRulesPolicy()) {
            log("Intersection markers are only available when TRAFFIC_RULES coordination is active.");
            if (viewportStatusLabel != null) viewportStatusLabel.setText("traffic rules only");
            return false;
        }

        Vector2D position = new Vector2D(tx, ty);
        boolean alreadyMarked = engine.hasTrafficRuleIntersection(tx, ty);
        if (!engine.getMap().isTraversable(position)) {
            log("Intersection markers can only be placed on traversable floor tiles.");
            if (viewportStatusLabel != null) viewportStatusLabel.setText("intersection requires floor tile");
            return false;
        }

        if (!alreadyMarked) {
            boolean hasBlockingOccupant = engine.getMap().getEntitiesAt(position).stream()
                    .anyMatch(entity -> entity instanceof Robot
                            || entity instanceof ChargingStation
                            || entity instanceof DeliveryStation);
            if (hasBlockingOccupant) {
                log("Intersection markers cannot be placed on robots or station tiles.");
                if (viewportStatusLabel != null) viewportStatusLabel.setText("intersection requires empty floor");
                return false;
            }
        }

        if (!engine.toggleTrafficRuleIntersection(tx, ty)) {
            log("Could not update the traffic intersection at tile (" + tx + ", " + ty + ").");
            return false;
        }

        drawViewport();
        log((alreadyMarked ? "Removed" : "Added") + " INTERSECTION at tile (" + tx + ", " + ty + ").");
        return true;
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

        if (pickingRack != null && e.getButton() == MouseButton.PRIMARY) {
            MapEntity hit = entityAtScreenPos(e.getX(), e.getY());
            if (hit instanceof DeliveryStation ds) {
                if (pickingSlot >= 0 && pickingSlot < pickingRack.getValidDropoffIds().size()) {
                    pickingRack.getValidDropoffIds().set(pickingSlot, ds.getId());
                    log("Assigned " + ds.getName() + " to slot " + pickingSlot
                            + " of rack " + pickingRack.getName() + ".");
                    Rack rackRef = pickingRack;
                    cancelDropoffPicking();
                    if (selectedEntity == rackRef) showPropertiesFor(rackRef);
                    persistEditorChanges();
                } else {
                    log("⚠ Dropoff assignment slot " + pickingSlot + " is no longer valid for rack "
                            + pickingRack.getName() + ".");
                    cancelDropoffPicking();
                }
            } else {
                cancelDropoffPicking();
                log("Dropoff assignment cancelled.");
            }
            return;
        }

        if (e.getButton() == MouseButton.PRIMARY) {
            Vector2D intersectionHit = intersectionAtScreenPos(e.getX(), e.getY());
            if (intersectionHit != null) {
                selectIntersection(intersectionHit);
                draggingOnCanvas = null;
                dragStartPosition = null;
            } else {
                // Left click: select. If on an entity, also arm for drag.
                MapEntity entityHit = entityAtScreenPos(e.getX(), e.getY());
                if (entityHit != null) {
                    selectEntity(entityHit);
                    draggingOnCanvas = entityHit;
                    dragStartPosition = new com.openrobotics.map.Vector2D(
                            (int) entityHit.getPosition().getX(),
                            (int) entityHit.getPosition().getY());
                } else {
                    clearSelection();
                    draggingOnCanvas = null;
                    dragStartPosition = null;
                }
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            // Right click: pan mode
            draggingOnCanvas = null;
            dragStartPosition = null;
            dragStartPosition = null;
        }

        // Update RAM display
        updateRamLabel();
    }

    private void onViewportMouseDragged(MouseEvent e) {
        double dx = e.getX() - lastMouseX;
        double dy = e.getY() - lastMouseY;

        if (draggingOnCanvas != null && e.getButton() == MouseButton.PRIMARY) {
            if (isEditorLocked()) {
                draggingOnCanvas = null;
                dragStartPosition = null;
                log("\u26a0 Cannot move objects while the simulation is running or has advanced past tick 0. Reset first.");
            } else {
                // Left-drag: move selected entity – snap to nearest tile, clamped to map bounds
                double tileSize = 32 * zoom;
                int newTX = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
                int newTY = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);
                if (canPlaceEntityAt(draggingOnCanvas, newTX, newTY, draggingOnCanvas)) {
                    draggingOnCanvas.setPosition(new com.openrobotics.map.Vector2D(newTX, newTY));
                    if (viewportStatusLabel != null)
                        viewportStatusLabel.setText(
                                "dragging(" + draggingOnCanvas.getName()
                                + ")  →  (" + newTX + ", " + newTY + ")");
                } else if (viewportStatusLabel != null) {
                    viewportStatusLabel.setText("blocked at (" + newTX + ", " + newTY + ")");
                }
                drawViewport();
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            // Right-drag: pan the viewport
            viewOffsetX += dx;
            viewOffsetY += dy;
            drawViewport();
        }

        lastMouseX = e.getX();
        lastMouseY = e.getY();

        // Update RAM display
        updateRamLabel();
    }

    private void onViewportMouseReleased(MouseEvent e) {
        if (draggingOnCanvas != null) {
            com.openrobotics.map.Vector2D endPos = draggingOnCanvas.getPosition();
            if (dragStartPosition != null && !dragStartPosition.equals(endPos)) {
                pushAction(new MoveAction(draggingOnCanvas, dragStartPosition, endPos));
                // Sync tasks to reflect the entity's new position
                syncTaskPositions(dragStartPosition, endPos);
            }
            log("Moved " + draggingOnCanvas.getName()
                + " to tile (" + (int)endPos.getX() + ", " + (int)endPos.getY() + ").");
            draggingOnCanvas = null;
            dragStartPosition = null;
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            if (viewportModeLabel   != null) viewportModeLabel.setText("right-click to pan, left-click to select");

            // Persist editor changes so the play-button snapshot and restart are always fresh
            persistEditorChanges();
        }

        // Update RAM display
        updateRamLabel();
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

    private Vector2D intersectionAtScreenPos(double sx, double sy) {
        if (engine == null || !engine.usesTrafficRulesPolicy()) return null;

        double tileSize = 32 * zoom;
        double markerInset = Math.max(3.0, tileSize * 0.22);
        double radius = (tileSize - 2 * markerInset) / 2.0;

        for (Vector2D intersection : engine.getTrafficRuleIntersections()) {
            double centerX = viewOffsetX + (intersection.getX() + entityOffsetTileX) * tileSize + tileSize / 2.0;
            double centerY = viewOffsetY + (intersection.getY() + entityOffsetTileY) * tileSize + tileSize / 2.0;
            double dx = sx - centerX;
            double dy = sy - centerY;
            if ((dx * dx) + (dy * dy) <= radius * radius) {
                return intersection;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Selection & Properties panel
    // ------------------------------------------------------------------ //

    private void deleteSelected() {
        if (selectedIntersection != null) {
            if (engine != null && engine.toggleTrafficRuleIntersection(
                    selectedIntersection.getX(), selectedIntersection.getY())) {
                log("Deleted INTERSECTION at tile (" + selectedIntersection.getX() + ", " + selectedIntersection.getY() + ").");
            }
            selectedIntersection = null;
            populateOutliner();
            drawViewport();
            return;
        }
        if (selectedEntity == null) return;
        if (guardEditor("delete")) return;
        MapEntity deleted = selectedEntity;
        log("Deleted " + deleted.getName() + ".");
        if (engine != null) {
            engine.removeEntity(deleted);
        }
        selectedEntity = null;
        populateOutliner();
        drawViewport();
        pushAction(new DeleteAction(deleted));
    }

    private void copySelected() {
        if (selectedEntity == null) return;
        clipboardEntity = selectedEntity;
        log("Copied " + clipboardEntity.getName() + ".");
    }

    private void pasteClipboard() {
        if (clipboardEntity == null || engine == null || engine.getMap() == null) return;
        if (guardEditor("paste")) return;
        int newX = clampTileX((int)clipboardEntity.getPosition().getX() + 1);
        int newY = clampTileY((int)clipboardEntity.getPosition().getY() + 1);
        MapEntity copy = createEntityFromType(
                clipboardEntity instanceof Robot ? "ROBOT" :
                        clipboardEntity instanceof ChargingStation ? "CHARGER" :
                                clipboardEntity instanceof DeliveryStation ? "STATION" :
                                        clipboardEntity instanceof Rack ? "RACK" : "OBSTACLE",
                newX, newY, clipboardEntity.getName() + "_copy");
        if (copy != null && canPlaceEntityAt(copy, newX, newY, null)) {
            engine.addEntity(copy);
            selectEntity(copy);
            populateOutliner();
            drawViewport();
            log("Pasted " + copy.getName() + " at tile (" + newX + ", " + newY + ").");
            pushAction(new AddAction(copy));
        } else {
            log("Paste blocked at tile (" + newX + ", " + newY + "). Only Robot + ChargingStation/DeliveryStation can share a tile.");
        }
    }

    private MapEntity clipboardEntity = null;

    private void clearSelection() {
        selectedEntity = null;
        selectedIntersection = null;
        if (outlinerListView != null)
            outlinerListView.getSelectionModel().clearSelection();
        clearPropertiesPanel();
        updateSelectionLabel();
        drawViewport();
    }

    private void selectEntity(MapEntity entity) {
        selectedEntity = entity;
        selectedIntersection = null;

        if (entity != null) {
            if (engine != null && engine.getMap() != null) {
                int idx = engine.getMap().getEntities().indexOf(entity);
                if (outlinerListView != null && idx >= 0)
                    outlinerListView.getSelectionModel().select(idx);
            }
            showPropertiesFor(entity);
        } else {
            clearSelection();
            return;
        }
        drawViewport();
    }

    private void selectIntersection(Vector2D intersection) {
        selectedEntity = null;
        selectedIntersection = intersection;
        if (outlinerListView != null)
            outlinerListView.getSelectionModel().clearSelection();
        showIntersectionProperties(intersection);
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
        // Commit rename whenever the text changes (covers programmatic setText in tests and
        // direct keyboard editing) as well as on focus-lost for undo-history bookkeeping.
        nameField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isBlank() || newText.equals(entity.getName())) return;
            if (guardEditor("rename")) { nameField.setText(entity.getName()); return; }
            String oldVal = entity.getName();
            entity.setName(newText);
            populateOutliner();
            drawViewport();
            pushAction(new RenameAction(entity, oldVal, newText));
        });
        nameField.setOnAction(ev -> nameField.getParent().requestFocus()); // Enter commits
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
            if (newVal == null || oldVal == null) return;
            if (guardEditor("move")) { xSpinner.getValueFactory().setValue(oldVal); return; }
            int targetX = newVal;
            int targetY = (int) entity.getPosition().getY();
            com.openrobotics.map.Vector2D oldPos = new com.openrobotics.map.Vector2D(oldVal, targetY);
            if (canPlaceEntityAt(entity, targetX, targetY, entity)) {
                com.openrobotics.map.Vector2D newPos = new com.openrobotics.map.Vector2D(targetX, targetY);
                entity.setPosition(newPos);
                syncTaskPositions(oldPos, newPos);
                persistEditorChanges();
                drawViewport();
            } else {
                xSpinner.getValueFactory().setValue(oldVal);
                log("Move blocked at tile (" + targetX + ", " + targetY + "). Only Robot + ChargingStation/DeliveryStation can share a tile.");
            }
        });
        ySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || oldVal == null) return;
            if (guardEditor("move")) { ySpinner.getValueFactory().setValue(oldVal); return; }
            int targetX = (int) entity.getPosition().getX();
            int targetY = newVal;
            com.openrobotics.map.Vector2D oldPos = new com.openrobotics.map.Vector2D(targetX, oldVal);
            if (canPlaceEntityAt(entity, targetX, targetY, entity)) {
                com.openrobotics.map.Vector2D newPos = new com.openrobotics.map.Vector2D(targetX, targetY);
                entity.setPosition(newPos);
                syncTaskPositions(oldPos, newPos);
                persistEditorChanges();
                drawViewport();
            } else {
                ySpinner.getValueFactory().setValue(oldVal);
                log("Move blocked at tile (" + targetX + ", " + targetY + "). Only Robot + ChargingStation/DeliveryStation can share a tile.");
            }
        });
        posBox.getChildren().addAll(
                new Label("Position:"),
                new Label("X:"), xSpinner,
                new Label("Y:"), ySpinner
        );
        propertiesPanel.getChildren().add(posBox);

        if (entity instanceof Robot robot) {
            // Navigation algorithm dropdown
            HBox algoBox = new HBox(8);
            algoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            ComboBox<String> algoCombo = new ComboBox<>(
                    FXCollections.observableArrayList("GREEDY", "BUG", "RTA_STAR"));
            algoCombo.setPrefWidth(120);
            // Determine current algorithm from the robot's nav strategy
            String detectedAlgo = robot.getNav() != null ? robot.getNav().toString() : "GREEDY";
            final String currentAlgo = algoCombo.getItems().contains(detectedAlgo) ? detectedAlgo : "GREEDY";
            algoCombo.setValue(currentAlgo);
            algoCombo.setOnAction(ev -> {
                if (guardEditor("change algorithm")) { algoCombo.setValue(currentAlgo); return; }
                String selected = algoCombo.getValue();
                String oldAlgo = robot.getNav() != null ? robot.getNav().toString() : "GREEDY";
                if (selected.equals(oldAlgo)) return;
                com.openrobotics.robot.navigation.NavigationStrategy oldNav = robot.getNav();
                long seed = engine != null ? engine.getSeed() : 42;
                robot.setNav(com.openrobotics.robot.navigation.NavigationStrategy.create(
                        com.openrobotics.robot.AlgorithmType.fromConfigString(selected), seed));
                log("Set " + robot.getName() + " algorithm to " + selected);
                pushAction(new AlgorithmChangeAction(robot, oldAlgo, selected, oldNav, robot.getNav()));
            });
            algoBox.getChildren().addAll(new Label("Algorithm:"), algoCombo);
            propertiesPanel.getChildren().add(algoBox);

            // Sensor dropdown
            HBox sensorBox = new HBox(8);
            sensorBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            ComboBox<String> sensorCombo = new ComboBox<>(
                    FXCollections.observableArrayList("PROXIMITY", "RANGE"));
            sensorCombo.setPrefWidth(120);
            String detectedSensor = robot.getSensor() != null ? robot.getSensor().toString() : "PROXIMITY";
            final String currentSensor = sensorCombo.getItems().contains(detectedSensor) ? detectedSensor : "PROXIMITY";
            sensorCombo.setValue(currentSensor);
            sensorCombo.setOnAction(ev -> {
                if (guardEditor("change sensor")) { sensorCombo.setValue(currentSensor); return; }
                String selected = sensorCombo.getValue();
                String oldSensor = robot.getSensor() != null ? robot.getSensor().toString() : "PROXIMITY";
                if (selected.equals(oldSensor)) return;
                com.openrobotics.robot.sensors.SensorStrategy oldSensorStrat = robot.getSensor();
                robot.setSensor(com.openrobotics.robot.sensors.SensorStrategy.create(
                        com.openrobotics.robot.SensorType.fromConfigString(selected)));
                log("Set " + robot.getName() + " sensor to " + selected);
                pushAction(new SensorChangeAction(robot, oldSensor, selected, oldSensorStrat, robot.getSensor()));
            });
            sensorBox.getChildren().addAll(new Label("Sensor:"), sensorCombo);
            propertiesPanel.getChildren().add(sensorBox);

            // Battery level spinner
            HBox batteryBox = new HBox(8);
            batteryBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            float maxBattery = robot.getConfig().batteryCapacity;
            Spinner<Double> batterySpinner = new Spinner<>(
                    new SpinnerValueFactory.DoubleSpinnerValueFactory(0, maxBattery, robot.getBattery(), 1.0));
            batterySpinner.setPrefWidth(90);
            batterySpinner.setEditable(true);
            batterySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && oldVal != null && !newVal.equals(oldVal)) {
                    if (guardEditor("change battery")) {
                        batterySpinner.getValueFactory().setValue(oldVal);
                        return;
                    }
                    robot.setBattery(newVal.floatValue());
                    drawViewport();
                    pushAction(new BatteryChangeAction(robot, oldVal.floatValue(), newVal.floatValue()));
                }
            });
            batteryBox.getChildren().addAll(new Label("Battery:"), batterySpinner);
            propertiesPanel.getChildren().add(batteryBox);

            // Read-only state display
            Label stateLabel = new Label("State: " + robot.getState());
            stateLabel.setStyle("-fx-text-fill: #666;");
            propertiesPanel.getChildren().add(stateLabel);
        } else if (entity instanceof Station) {
            Label stationProps = new Label("Station configuration");
            propertiesPanel.getChildren().add(stationProps);
        } else if (entity instanceof Rack rack) {
            boolean globalManual = engine != null && engine.isManualTaskAssignment();

            if (globalManual) {
                // ── Number of boxes (manual mode only) ──
                HBox boxCountBox = new HBox(8);
                boxCountBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Spinner<Integer> boxCountSpinner = new Spinner<>(
                        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, rack.getBoxCount()));
                boxCountSpinner.setPrefWidth(80);
                boxCountSpinner.setEditable(true);
                boxCountSpinner.valueProperty().addListener((obs, oldV, newV) -> {
                    if (newV == null || oldV == null || newV.equals(oldV)) return;
                    if (guardEditor("change box count")) {
                        boxCountSpinner.getValueFactory().setValue(oldV);
                        return;
                    }
                    rack.setBoxCount(newV);
                    persistEditorChanges();
                });
                boxCountBox.getChildren().addAll(new Label("Number of boxes:"), boxCountSpinner);
                propertiesPanel.getChildren().add(boxCountBox);

                // ── Manual dropoff assignment checkbox (manual mode only) ──
                HBox manualBox = new HBox(8);
                manualBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                CheckBox manualCheck = new CheckBox("Manual dropoff assignment");
                manualCheck.setSelected(rack.isManualDropoffAssignment());
                VBox dropoffArrayBox = new VBox(4);
                dropoffArrayBox.setVisible(rack.isManualDropoffAssignment());
                dropoffArrayBox.setManaged(rack.isManualDropoffAssignment());
                manualCheck.selectedProperty().addListener((obs, oldV, newV) -> {
                    if (guardEditor("toggle manual assignment")) {
                        manualCheck.setSelected(oldV);
                        return;
                    }
                    rack.setManualDropoffAssignment(newV);
                    dropoffArrayBox.setVisible(newV);
                    dropoffArrayBox.setManaged(newV);
                    renderRackDropoffArray(rack, dropoffArrayBox);
                    persistEditorChanges();
                });
                manualBox.getChildren().add(manualCheck);
                propertiesPanel.getChildren().add(manualBox);

                // ── Dropoff array (shown only when manual checkbox is on) ──
                renderRackDropoffArray(rack, dropoffArrayBox);
                propertiesPanel.getChildren().add(dropoffArrayBox);
            }
        }
    }

    private void renderRackDropoffArray(Rack rack, VBox container) {
        container.getChildren().clear();

        // Header
        HBox header = new HBox(6);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label headerLabel = new Label("Valid Dropoff Points");
        headerLabel.setStyle("-fx-font-weight: bold;");
        Tooltip headerTip = new Tooltip(
            "Boxes are distributed across the valid dropoff points using round-robin. " +
            "Null slots are ignored. At least one non-null slot is required to play.");
        Tooltip.install(headerLabel, headerTip);
        Button addBtn = new Button("+");
        addBtn.setOnAction(ev -> {
            if (guardEditor("add dropoff slot")) return;
            rack.getValidDropoffIds().add(null);
            renderRackDropoffArray(rack, container);
            persistEditorChanges();
        });
        header.getChildren().addAll(headerLabel, addBtn);
        container.getChildren().add(header);

        // Build station lookup
        java.util.Map<java.util.UUID, DeliveryStation> stationById = new java.util.HashMap<>();
        if (engine != null && engine.getMap() != null) {
            for (MapEntity e : engine.getMap().getEntities()) {
                if (e instanceof DeliveryStation ds) stationById.put(ds.getId(), ds);
            }
        }

        List<java.util.UUID> ids = rack.getValidDropoffIds();
        for (int i = 0; i < ids.size(); i++) {
            final int slotIndex = i;
            java.util.UUID id = ids.get(i);
            HBox row = new HBox(6);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label idxLabel = new Label("[" + i + "]");
            idxLabel.setStyle("-fx-text-fill: #666;");

            String display;
            if (id == null) {
                display = "(unset)";
            } else {
                DeliveryStation ds = stationById.get(id);
                display = ds == null ? "(deleted station)"
                        : ds.getName() + " (" + (int)ds.getPosition().getX()
                          + ", " + (int)ds.getPosition().getY() + ")";
            }
            Label displayLabel = new Label(display);
            if (id == null || stationById.get(id) == null) {
                displayLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #999;");
            }

            Button assignBtn = new Button("Assign");
            assignBtn.setOnAction(ev -> beginDropoffPicking(rack, slotIndex));

            Button removeBtn = new Button("\u2715");
            removeBtn.setOnAction(ev -> {
                if (guardEditor("remove dropoff slot")) return;
                rack.getValidDropoffIds().remove(slotIndex);
                renderRackDropoffArray(rack, container);
                persistEditorChanges();
            });

            row.getChildren().addAll(idxLabel, displayLabel, assignBtn, removeBtn);
            container.getChildren().add(row);
        }
    }

    private void beginDropoffPicking(Rack rack, int slotIndex) {
        this.pickingRack = rack;
        this.pickingSlot = slotIndex;
        if (tipLabel != null) {
            tipLabel.setText("TIP: Click a delivery station to assign it to slot #"
                + slotIndex + ". Click elsewhere to cancel.");
        }
        viewportStack.setCursor(javafx.scene.Cursor.CROSSHAIR);
        drawViewport();
    }

    private void cancelDropoffPicking() {
        this.pickingRack = null;
        this.pickingSlot = -1;
        if (tipLabel != null) tipLabel.setText("");
        viewportStack.setCursor(javafx.scene.Cursor.DEFAULT);
        drawViewport();
    }

    private void showIntersectionProperties(Vector2D intersection) {
        if (propertiesPanel == null || intersection == null) return;
        propertiesPanel.getChildren().clear();

        Label title = new Label("Traffic Intersection");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        propertiesPanel.getChildren().add(title);

        Separator sep = new Separator();
        propertiesPanel.getChildren().add(sep);

        HBox typeBox = new HBox(8);
        typeBox.getChildren().addAll(
                new Label("Type:"),
                new Label("Intersection Marker") {{ setStyle("-fx-text-fill: #666;"); }}
        );
        propertiesPanel.getChildren().add(typeBox);

        HBox posBox = new HBox(8);
        posBox.getChildren().addAll(
                new Label("Position:"),
                new Label(intersection.toString()) {{ setStyle("-fx-text-fill: #3D3C39; -fx-font-family: monospace;"); }}
        );
        propertiesPanel.getChildren().add(posBox);

        Label hint = new Label("Press Delete to remove this intersection.");
        hint.setStyle("-fx-text-fill: #666;");
        propertiesPanel.getChildren().add(hint);
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
        String type = source.getUserData() instanceof String s ? s : null;
        if (type == null || type.isBlank()) {
            log("⚠ Add Object tile click ignored because the tile type is missing.");
            return;
        }

        if (e.getClickCount() == 2) {
            log("Opened description for " + type + " from the Add Object panel.");
            ScreenNavigator.openDialog(
                    ScreenNavigator.DIALOG_OBJECT_DESC, type + " – Description");
        } else {
            log("Selected object type: " + type + ".  Drag it into the viewport to place.");
        }
    }

    /** Handles selection in the Outliner list. */
    @FXML
    private void onOutlinerSelect(MouseEvent e) {
        onOutlinerSelect();
    }

    private void onOutlinerSelect() {
        if (outlinerListView == null) return;
        int idx = outlinerListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= outlinerBacking.size()) {
            log("⚠ Ignored outliner selection because the selected index was out of range.");
            return;
        }

        Object selected = outlinerBacking.get(idx);
        if (selected instanceof MapEntity mapEntity) {
            selectEntity(mapEntity);
        } else if (selected instanceof Task task) {
            log("Selected task #" + task.getId() + " in the outliner. Task entries are read-only here.");
        } else if (selected != null) {
            log("⚠ Ignored outliner selection of unsupported entry type: " + selected.getClass().getSimpleName());
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
            String icon  = (e instanceof Robot)    ? "\ud83e\udd16 "  // 🤖 robot
                    : (e instanceof Rack)     ? "\ud83d\udce6 "  // 📦 rack/shelf
                    : (e instanceof Station)  ? "\u26a1 "        // ⚡ station
                    : (e instanceof Obstacle) ? "\ud83e\uddf1 "  // 🧱 wall/obstacle
                    :                           "\u25ab ";        // ▫ generic entity
            String entry = icon + e.getName() + "  " + e.getPosition();
            if (filter.isEmpty() || entry.toLowerCase().contains(filter)) {
                newItems.add(entry);
                newBacking.add(e);
            }
        }

        if (engine.getDispatcher() != null) {
            for (Task task : engine.getDispatcher().getLifetimeTasks()) {
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
        if (engine.isFinished()) {
            log("\u26a0 Simulation already complete. Press Stop to reset before playing again.");
            if (playBtn != null) playBtn.setDisable(true);
            return;
        }

        logPollingTimeline.play(); // start regular polling

        if (running) {
            if (paused) {
                paused = false;
                if (simStatusLabel != null) {
                    simStatusLabel.setText("RUNNING");
                    simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
                }
                playBtn.setStyle("-fx-background-color: #1a743f;");
                pauseBtn.setStyle("-fx-background-color: #c9605a;");
                startLoop();
                log("Simulation resumed.");
                // Update RAM display
                updateRamLabel();
            }
        } else {
            // Ensure a baseline snapshot exists before starting (editor actions save it continuously,
            // but if no edits were made this is the first snapshot).
            if (initialSnapshotPath == null && engine != null) {
                saveEditorBaseline();
            }



            running = true;
            paused  = false;
            if (simStatusLabel != null) {
                simStatusLabel.setText("RUNNING");
                simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
            }

            playBtn.setStyle("-fx-background-color: #1a743f;");
            pauseBtn.setStyle("-fx-background-color: #c9605a;");

            // ── Validate manual-mode racks before generating tasks ──
            if (engine.getMap() != null) {
                java.util.Set<java.util.UUID> stationIds = new java.util.HashSet<>();
                for (MapEntity me : engine.getMap().getEntities()) {
                    if (me instanceof DeliveryStation ds) stationIds.add(ds.getId());
                }
                List<String> offenders = new ArrayList<>();
                for (MapEntity me : engine.getMap().getEntities()) {
                    if (me instanceof Rack r && r.isManualDropoffAssignment()) {
                        boolean hasValid = r.getValidDropoffIds().stream()
                            .anyMatch(uid -> uid != null && stationIds.contains(uid));
                        if (!hasValid) offenders.add(r.getName());
                    }
                }
                if (!offenders.isEmpty()) {
                    running = false;
                    paused = false;
                    if (playBtn  != null) playBtn.setStyle("");
                    if (pauseBtn != null) pauseBtn.setStyle("");
                    if (simStatusLabel != null) {
                        simStatusLabel.setText("STOPPED");
                        simStatusLabel.setStyle("-fx-text-fill: #D6453D; -fx-font-weight: bold;");
                    }
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Cannot start simulation");
                    alert.setHeaderText("Manual-mode racks have no valid dropoff points");
                    alert.setContentText(
                        "The following racks use Manual Dropoff Assignment but their pool is empty or all-null:\n\n  \u2022 "
                        + String.join("\n  \u2022 ", offenders)
                        + "\n\nAssign at least one delivery station per rack, or disable Manual Dropoff Assignment.");
                    alert.showAndWait();
                    log("\u26a0 Play aborted: " + offenders.size() + " rack(s) have an empty manual dropoff pool.");
                    return;
                }
            }

            // Generate tasks if the dispatcher is empty.
            // Automatic mode: exactly engine.getMaxTasks() tasks, random rack+station pairs.
            // Manual mode: one task per rack box using each rack's configured dropoff pool.
            if (engine.getDispatcher().getAllQueuedTasks().isEmpty() && engine.getMap() != null) {
                List<Task> generated;
                if (engine.isManualTaskAssignment()) {
                    generated = com.openrobotics.task.TaskGenerator.generateRandomTasks(
                            engine.getMap(), Integer.MAX_VALUE, engine.getSeed());
                } else {
                    generated = com.openrobotics.task.TaskGenerator.generateAutomaticTasks(
                            engine.getMap(), engine.getMaxTasks(), engine.getSeed());
                }
                if (!generated.isEmpty()) {
                    engine.getDispatcher().addTasks(generated);
                    log("Auto-generated " + generated.size() + " tasks from map racks and delivery stations.");
                } else {
                    log("\u26a0 No tasks could be generated. Ensure the map has at least one rack and one delivery station.");
                }
            }

            // Logging simulation run start event
            SimulationRunRecordBuilder simRunRecordBuilder = new SimulationRunRecordBuilder(engine);
            SimulationRunRecord record = simRunRecordBuilder.buildSimulationStartRecord();
            Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, record);

            // Logging task creation events for existing tasks in dispatcher at simulation start
            List<Task> existingTasks = engine.getDispatcher().getAllQueuedTasks();

            for (Task task : existingTasks) {
                WorkloadTaskRecordBuilder taskRecordBuilder = new WorkloadTaskRecordBuilder(engine.getRunId(), task);
                WorkloadTaskRecord taskRecord = taskRecordBuilder.buildTaskCreationRecord(engine.getTickCounter());
                long artificialId = Logger.logTaskEvent(TaskEvent.TASK_CREATED, taskRecord);
                task.setArtificialId(artificialId); // setting artificial ID for tracking in logs
            }

            startLoop();
            log("Simulation started.");
            // Update RAM display
            updateRamLabel();
        }
    }

    @FXML
    private void onPause() {
        logPollingTimeline.stop(); // stop regular polling for sim logs

        if (running && !paused) {
            paused = true;
            stopLoop();
            if (simStatusLabel != null) {
                simStatusLabel.setText("PAUSED");
                simStatusLabel.setStyle("-fx-text-fill: #E0B200; -fx-font-weight: bold;");
            }
            playBtn.setStyle("-fx-background-color: #599068;");
            pauseBtn.setStyle("-fx-background-color: #C0392B;");
            log("Simulation paused.");
            // Update RAM display
            updateRamLabel();
        }
    }

    @FXML
    private void onStop() {
        running = false;
        paused  = false;
        stopLoop();
        if (playBtn != null) playBtn.setDisable(false);
        if (simStatusLabel != null) {
            simStatusLabel.setText("STOPPED");
            simStatusLabel.setStyle("-fx-text-fill: #D6453D; -fx-font-weight: bold;");
        }
        if (playBtn  != null) playBtn.setStyle("");
        if (pauseBtn != null) pauseBtn.setStyle("");
        log("Simulation stopped.");
        // Update RAM display
        updateRamLabel();
    }

    @FXML
    private void onRestart() {
        // If already at tick 0, not running, and there is nothing to reload, nothing to reset.
        String earlyReloadPath = initialSnapshotPath != null ? initialSnapshotPath : AppState.getConfigPath();
        if (localTick == 0 && !running && !simulationFailed && earlyReloadPath == null) {
            log("Already at tick 0. Nothing to reset.");
            return;
        }
        if (animTimeline != null) { animTimeline.stop(); animTimeline = null; }
        animating = false;
        animationProgress = 0.0;
        prevRobotPositions.clear();
        selectedEntity = null;
        undoStack.clear();
        redoStack.clear();
        onStop();
        localTick = 0;
        simulationFailed = false;
        lastSeenLogId = 0;
        AppState.setSimulationTick(0);
        // Reload from the editor baseline snapshot (continuously updated on every editor action).
        // This restores the most recent editor state, not the original config file.
        String reloadPath = initialSnapshotPath != null ? initialSnapshotPath : AppState.getConfigPath();
        if (reloadPath != null) {
            SimulationEngine reloaded = new SimulationEngine(reloadPath);
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

        // If no engine is loaded, restart still resets the UI state safely.
        if (engine == null) {
            if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
            if (simProgressBar != null) simProgressBar.setProgress(0);
            if (simStatusLabel != null) {
                simStatusLabel.setText("READY");
                simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
            }
            log("Simulation reset.");
            return;
        }

        // Resetting some internal engine state for new simulation run
        engine.reset();

        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
        if (simProgressBar != null) simProgressBar.setProgress(0);
        if (simStatusLabel != null) {
            simStatusLabel.setText("READY");
            simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
        }
        log("Simulation reset.");
        // Update RAM display
        updateRamLabel();
        populateOutliner();
        drawViewport();

        // Reset simulation logs text area
        if (logArea != null) {
            System.out.println("[SimulationController] Clearing log area on simulation reset.");
            logArea.clear();
        }
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
        // Update RAM display
        updateRamLabel();

        Logger.flushRobotEvents(); // ensure all robot events are flushed after stepping
        fetchLogsAsync(); // fetch logs after stepping to get latest events
    }

    @FXML private void onSpeed1() { setSpeed(1); log("Speed set to ×1."); }
    @FXML private void onSpeed2() { setSpeed(2); log("Speed set to ×2."); }
    @FXML private void onSpeed3() { setSpeed(3); log("Speed set to ×3."); }
    @FXML private void onSpeed10() { setSpeed(10); log("Speed set to ×10."); }

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
        logPollingTimeline.stop(); // stop regular polling for sim logs

        if (simLoop != null) {
            simLoop.stop();
            simLoop = null;
        }

        System.out.println("[SimulationController] Flushing logs and fetching remaining logs on stop...");
        Logger.flushRobotEvents(); // ensure all robot events are flushed when stopping
        fetchLogsSync(); // fetch any remaining logs synchronously on stop
    }

    /** Stops all timelines and unbinds canvas properties. Called by ScreenNavigator before replacing this screen. */
    @Override
    public void cleanup() {
        shutdown();
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
            // Sandbox mode: no robots means an empty map used for layout/stepping only —
            // treat the tick as a no-op success so the step counter still advances.
            if (engine.getSimulationError() == SimulationError.NO_ROBOTS_SPAWNED) {
                // fall through to the localTick++ / display-update block below
            } else if (engine.getSimulationError() != SimulationError.NONE) {
                handleSimulationFailure();
                return;
            } else {
                handleSimulationComplete();
                return;
            }
        }
        localTick++;

        AppState.setSimulationTick(localTick);

        populateOutliner();

        if (engine.getRobots() != null && engine.getRobots().length > 0) {
            startAnimation();
        } else {
            drawViewport();
        }

        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK " + localTick);
        if (simProgressBar != null) simProgressBar.setProgress(Math.min(1.0, localTick / 1000.0));
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
        if (playBtn  != null) { playBtn.setStyle(""); playBtn.setDisable(true); }
        if (pauseBtn != null) pauseBtn.setStyle("");
        log("Simulation complete at TICK " + localTick + ".");
    }

    /**
     * Handles simulation failure (e.g. all robots died)
     */
    private void handleSimulationFailure() {
        stopLoop();
        running = false;
        paused = false;
        simulationFailed = true;
        if (simStatusLabel != null) {
            simStatusLabel.setText("FAILURE");
            simStatusLabel.setStyle("-fx-text-fill: #599068; -fx-font-weight: bold;");
        }
        if (viewportStatusLabel != null) {
            viewportStatusLabel.setText("Workload failed to complete");
        }
        if (playBtn  != null) playBtn.setStyle("");
        if (pauseBtn != null) pauseBtn.setStyle("");
        log("Simulation failed: " + engine.getSimulationErrorMessage()); // logging simulation error message
        log("Simulation failed at TICK " + localTick + ".");
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
                    Duration.millis((frame + 1) * BASE_TICK_MS / speedFactor / totalFrames),
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
        double cx = warehouseCanvas.getWidth()  / 2;
        double cy = warehouseCanvas.getHeight() / 2;
        double oldZoom = zoom;
        zoom = Math.min(zoom * 1.2, 10.0);
        double r = zoom / oldZoom;
        viewOffsetX = cx - r * (cx - viewOffsetX);
        viewOffsetY = cy - r * (cy - viewOffsetY);
        drawViewport();
    }

    @FXML private void onZoomOut() {
        double cx = warehouseCanvas.getWidth()  / 2;
        double cy = warehouseCanvas.getHeight() / 2;
        double oldZoom = zoom;
        zoom = Math.max(zoom / 1.2, 0.2);
        double r = zoom / oldZoom;
        viewOffsetX = cx - r * (cx - viewOffsetX);
        viewOffsetY = cy - r * (cy - viewOffsetY);
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
        if (sidebarPanel == null || mainSplitPane == null) return;
        if (toggleSidebarItem.isSelected()) {
            // Restore: reposition divider after the current layout pass
            Platform.runLater(() -> mainSplitPane.setDividerPosition(0, savedSidebarDivider));
        } else {
            // Collapse: save position, then push divider to 0
            if (mainSplitPane.getDividerPositions().length > 0) {
                savedSidebarDivider = mainSplitPane.getDividerPositions()[0];
            }
            Platform.runLater(() -> mainSplitPane.setDividerPosition(0, 0.0));
        }
    }

    @FXML
    private void onToggleConsole() {
        if (consoleShell == null || viewportConsoleSplit == null) return;
        if (toggleConsoleItem.isSelected()) {
            // Restore: reposition divider after the current layout pass
            Platform.runLater(() -> viewportConsoleSplit.setDividerPosition(0, savedConsoleDivider));
        } else {
            // Collapse: save position, then push divider to 1.0
            if (viewportConsoleSplit.getDividerPositions().length > 0) {
                savedConsoleDivider = viewportConsoleSplit.getDividerPositions()[0];
            }
            Platform.runLater(() -> viewportConsoleSplit.setDividerPosition(0, 1.0));
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

    @FXML
    private void onLoadConfig() {
        javafx.fxml.FXMLLoader loader = ScreenNavigator.openDialog(
                ScreenNavigator.DIALOG_LOAD_CONFIG, "Load Configuration");
        Object ctrl = loader.getController();
        if (!(ctrl instanceof LoadConfigController lcc)) return;
        java.io.File file = lcc.getSelectedFile();
        if (file == null) return;

        // Full reset: stop the loop, clear transient editor state, drop undo/redo,
        // then rebuild the engine from the chosen file.
        stopLoop();
        running = false;
        paused = false;
        localTick = 0;
        AppState.setSimulationTick(0);
        selectedEntity = null;
        undoStack.clear();
        redoStack.clear();

        SimulationEngine loaded = new SimulationEngine(file.getAbsolutePath());
        if (loaded == null || loaded.getMap() == null || loaded.getInitError() != null) {
            log("\u26a0 Load failed: "
                    + (loaded != null && loaded.getInitError() != null ? loaded.getInitError() : "unknown error"));
            return;
        }
        engine = loaded;
        AppState.setEngine(engine);
        AppState.setConfigPath(file.getAbsolutePath());
        initialSnapshotPath = null;
        saveEditorBaseline();

        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
        if (simProgressBar != null) simProgressBar.setProgress(0);
        if (simStatusLabel != null) {
            simStatusLabel.setText("READY");
            simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
        }
        populateOutliner();
        drawViewport();
        updateRamLabel();
        log("Loaded configuration from " + file.getAbsolutePath());
    }

    private void log(String message) {
        System.out.println("[SimulationController] " + message);
        if (consoleArea != null) consoleArea.appendText(message + "\n");
    }

    /**
     * Asynchronously fetches simulation logs from the database using a background thread to
     * avoid blocking/glitching the UI
     */
    public void fetchLogsAsync() {
        if (engine == null) return;
        javafx.concurrent.Task<Object> task = new javafx.concurrent.Task() {
            @Override
            protected Object call() {
                try {
                    long start = System.currentTimeMillis();
                    List<SimLogRecord> logs = SimLogDao.findLatestLogs(engine.getRunId(), lastSeenLogId);
                    System.out.println("[SimulationController] Fetched " + logs.size() + " log(s) asynchronously in " + (System.currentTimeMillis() - start) + " ms.");
                    return logs;
                } catch (Exception e) {
                    System.out.println("Error fetching logs from database: " + e.getMessage());
                    return null;
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<SimLogRecord> logs = (List<SimLogRecord>) task.getValue();
            long start = System.currentTimeMillis();
            updateLogsArea(logs); // safe: runs on UI thread
            System.out.println("[SimulationController] Updated sim log area in " + (System.currentTimeMillis() - start) + " ms.");
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
        });

        executor.submit(task); // runs the task on a background thread
    }

    /**
     * Synchronously fetches simulation logs from the database and updates the logs area.
     */
    public void fetchLogsSync() {
        if (engine == null) return;
        try {
            long start = System.currentTimeMillis();
            List<SimLogRecord> logs = SimLogDao.findLatestLogs(engine.getRunId(), lastSeenLogId);
            System.out.println("[SimulationController] Fetched " + logs.size() + " log(s) synchronously in " + (System.currentTimeMillis() - start) + " ms.");

            start = System.currentTimeMillis();
            updateLogsArea(logs);
            System.out.println("[SimulationController] Updated sim log area in " + (System.currentTimeMillis() - start) + " ms.");
        } catch (Exception e) {
            System.out.println("Error fetching logs from database: " + e.getMessage());
        }
    }

    /**
     * Updates the logs area with the provided list of SimLogRecords.
     * @param logs the list of SimLogRecords to display, or null if an error occurred during fetching
     */
    private void updateLogsArea(List<SimLogRecord> logs) {
        if (logs == null || logs.isEmpty()) return;

        StringBuilder sb = new StringBuilder(logs.size() * 80);

        for (SimLogRecord log : logs) {
            sb.append(log.toString()).append("\n");
            lastSeenLogId = log.getId();
        }

        logArea.appendText(sb.toString());
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

    /**
     * Toggles the shortcut reference overlay on/off.
     * The overlay sits inside viewportStack so it floats above the canvas.
     */
    @FXML
    private void onToggleShortcutOverlay() {
        boolean nowVisible = !shortcutOverlay.isVisible();
        shortcutOverlay.setVisible(nowVisible);
        shortcutOverlay.setManaged(nowVisible);
    }

    /**
     * Updates any pending tasks whose pickup or dropoff location matches {@code oldPos}
     * to use {@code newPos} instead. Called whenever an entity is moved in the editor
     * so that tasks remain aligned with the actual rack/station positions on the map.
     */
    private void syncTaskPositions(Vector2D oldPos, Vector2D newPos) {
        if (engine == null || engine.getDispatcher() == null) return;
        int updated = 0;
        for (Task task : engine.getDispatcher().getAllQueuedTasks()) {
            boolean changed = false;
            if (task.getPickupLocation().equals(oldPos)) {
                task.setPickupLocation(newPos);
                changed = true;
            }
            if (task.getDropoffLocation().equals(oldPos)) {
                task.setDropoffLocation(newPos);
                changed = true;
            }
            if (changed) updated++;
        }
        if (updated > 0) {
            log("Updated " + updated + " task(s) to reflect entity move from " + oldPos + " → " + newPos + ".");
            populateOutliner(); // refresh task list in the outliner
        }
    }

    /**
     * Saves the current engine state to a temp file and updates
     * so that pressing Play (or Restart) always uses the latest editor layout, including
     * any rack/entity moves made before the simulation has started.
     */
    private void persistEditorChanges() {
        if (engine == null || running) return; // never overwrite a running sim
        try {
            // Reuse the existing snapshot file if one already exists, otherwise create fresh
            String savePath = initialSnapshotPath != null ? initialSnapshotPath : AppState.getConfigPath();
            if (savePath == null) {
                java.io.File tmp = java.io.File.createTempFile("openrobotics_editor_", ".json");
                tmp.deleteOnExit();
                savePath = tmp.getAbsolutePath();
                initialSnapshotPath = savePath;
            }
            engine.configSaving(savePath);
        } catch (Exception ex) {
            log("\u26a0 Could not auto-save editor changes: " + ex.getMessage());
        }
    }

    private void updateRamLabel() {
        if (ramLabel != null) {
            long usedKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024;
            ramLabel.setText("RAM: " + usedKb + " KB");
        }
    }

    /**
     * Performs necessary cleanup when the application is closing
     */
    public void shutdown() {
        // stop log polling timeline
        if (logPollingTimeline != null) {
            logPollingTimeline.stop();
        }

        // shutdown the executor to stop any ongoing log fetching tasks
        executor.shutdownNow();

        Logger.flushRobotEvents(); // ensure all logs are flushed before shutdown
    }
}
