package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.db.dao.MapDao;
import com.openrobotics.db.dao.SimLogDao;
import com.openrobotics.db.model.MapRecord;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.recordbuilders.MapRecordBuilder;
import com.openrobotics.db.recordbuilders.SimulationRunRecordBuilder;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.eventtypes.SimulationRunEvent;
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
 * <p>Manages the simulation viewport and related UI controls, including:
 * <ul>
 *   <li>2D warehouse canvas rendering.</li>
 *   <li>Drag-and-drop placement of object tiles from the sidebar onto the canvas.</li>
 *   <li>Dragging existing objects within the canvas to reposition them.</li>
 *   <li>The Add Object and Outliner tabs.</li>
 *   <li>The object properties panel.</li>
 *   <li>Console output.</li>
 *   <li>Playback controls for play, pause, stop, restart, and speed changes.</li>
 *   <li>Live statistics, including tick, completed tasks, collisions, and status.</li>
 *   <li>Viewport navigation with right-click panning and scroll-wheel zoom.</li>
 * </ul>
 */
public class SimulationController implements ScreenNavigator.Cleanable {

    // Topbar Live Stats
    @FXML private Label objsLabel;
    @FXML private Label ramLabel;
    @FXML private Label canvasSizeLabel;

    // Simulation Stats
    @FXML private Label tickLabel;
    @FXML private Label tasksDoneLabel;
    @FXML private Label collisionsLabel;
    @FXML private Label simStatusLabel;

    // Outliner
    @FXML private TabPane          outlinerTabPane;
    @FXML private ListView<String> outlinerListView;
    @FXML private TextField        outlinerSearchField;
    @FXML private Button           intersectionObjectTile;

    // Viewport
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

    // Split Pane State
    // Saved divider positions so collapse/expand is smooth
    private double savedSidebarDivider  = 0.17;
    private double savedConsoleDivider  = 0.83;

    // Properties Panel
    @FXML private VBox propertiesPanel;
    @FXML private Label propDescLabel;
    @FXML private TextField strPropField;

    // Console
    @FXML private TextArea consoleArea;
    @FXML private Button    clearConsoleButton;

    // Simulation Logs
    @FXML private TextArea logArea;

    // Tab Strip
    @FXML private Button editorTabBtn;
    @FXML private Button resultsTabBtn;

    // Playback
    @FXML private Button      speed1Btn;
    @FXML private Button      speed2Btn;
    @FXML private Button      speed3Btn;
    @FXML private Button      speed10Btn;
    @FXML private Button      playBtn;
    @FXML private Button      pauseBtn;
    @FXML private ProgressBar simProgressBar;

    // Viewport State
    private double lastMouseX;
    private double lastMouseY;
    private double viewportMouseX = -1;  // Last known mouse X over the viewport (-1 means unset).
    private double viewportMouseY = -1;
    private double viewOffsetX = 0;
    private double viewOffsetY = 0;
    private double zoom        = 1.0;

    // Canvas State
    private int     canvasWidthTiles;
    private int     canvasHeightTiles;
    // Center loaded entities inside the canvas border.
    private int     entityOffsetTileX = 0;
    private int     entityOffsetTileY = 0;
    private boolean viewportCentered  = false;  // Auto-center only once after layout settles.

    // Simulation State
    private boolean running = false;
    private boolean paused  = false;
    private boolean simulationFailed = false;
    // Restart reloads the latest tick-0 editor snapshot rather than the original config path.
    private String initialSnapshotPath = null;

    // Engine Binding
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

    // Canvas Object State
    private final List<Object> outlinerBacking = new ArrayList<>();
    private MapEntity selectedEntity = null;
    private Rack pickingRack = null;
    private int  pickingSlot = -1;
    private Vector2D selectedIntersection = null;
    private MapEntity draggingOnCanvas = null;
    // Track the drag origin so related task endpoints can be updated on release.
    private com.openrobotics.map.Vector2D dragStartPosition = null;
    // Intersections are engine-managed coordinates, not MapEntity instances.
    private Vector2D dragStartIntersection = null;
    private Vector2D dragIntersectionOrigin = null;
    // Track copied entities and intersections separately.
    private MapEntity clipboardEntity = null;
    private Vector2D clipboardIntersection = null;
    private int nextObjId = 1;
    private Timeline tipRotationLoop;
    private int dragHighlightTileX = -1;
    private int dragHighlightTileY = -1;

    // Animation
    private boolean animating = false;
    private double animationProgress = 0.0;
    private Timeline animTimeline = null;
    private Map<java.util.UUID, com.openrobotics.map.Vector2D> prevRobotPositions = new HashMap<>();
    private static final int ANIMATION_FRAMES = 5;

    // Undo and Redo
    private final java.util.Deque<EditorAction> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<EditorAction> redoStack = new java.util.ArrayDeque<>();

    // Log Polling
    private Timeline logPollingTimeline;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long lastSeenLogId = 0;
    private boolean restoredOutputState = false;

    // Editor Actions
    private sealed interface EditorAction permits AddAction, DeleteAction, MoveAction, RenameAction, AlgorithmChangeAction, BatteryChangeAction, SensorChangeAction, IntersectionToggleAction, IntersectionMoveAction {
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
    // Undo/redo for intersection add/delete — toggle is its own inverse
    private record IntersectionToggleAction(Vector2D position, boolean wasAdded) implements EditorAction {
        public void undo(SimulationController ctrl) {
            if (ctrl.engine == null) return;
            ctrl.engine.toggleTrafficRuleIntersection((int) position.getX(), (int) position.getY());
            ctrl.drawViewport();
            ctrl.populateOutliner();
        }
        public void redo(SimulationController ctrl) {
            if (ctrl.engine == null) return;
            ctrl.engine.toggleTrafficRuleIntersection((int) position.getX(), (int) position.getY());
            ctrl.drawViewport();
            ctrl.populateOutliner();
        }
        public String description() { return (wasAdded ? "Add" : "Delete") + " INTERSECTION at " + position; }
    }

    // Undo/redo for intersection drag — stores original and final positions
    private record IntersectionMoveAction(Vector2D from, Vector2D to) implements EditorAction {
        public void undo(SimulationController ctrl) {
            if (ctrl.engine == null) return;
            ctrl.engine.toggleTrafficRuleIntersection((int) to.getX(), (int) to.getY());
            ctrl.engine.toggleTrafficRuleIntersection((int) from.getX(), (int) from.getY());
            ctrl.selectedIntersection = from;
            ctrl.drawViewport();
        }
        public void redo(SimulationController ctrl) {
            if (ctrl.engine == null) return;
            ctrl.engine.toggleTrafficRuleIntersection((int) from.getX(), (int) from.getY());
            ctrl.engine.toggleTrafficRuleIntersection((int) to.getX(), (int) to.getY());
            ctrl.selectedIntersection = to;
            ctrl.drawViewport();
        }
        public String description() { return "Move INTERSECTION from " + from + " to " + to; }
    }

    private void pushAction(EditorAction action) {
        // Any new edit becomes the latest undo point and invalidates redo history.
        undoStack.push(action);
        redoStack.clear();
        saveEditorBaseline();
    }

    private void undoAction() {
        if (undoStack.isEmpty()) { log("Nothing to undo."); return; }
        EditorAction action = undoStack.pop();
        action.undo(this);
        // Keep redo symmetrical with the action that was just unwound.
        redoStack.push(action);
        log("Undo: " + action.description());
        saveEditorBaseline();
    }

    private void redoAction() {
        if (redoStack.isEmpty()) { log("Nothing to redo."); return; }
        EditorAction action = redoStack.pop();
        action.redo(this);
        // A redone action becomes the newest entry in undo history again.
        undoStack.push(action);
        log("Redo: " + action.description());
        saveEditorBaseline();
    }

    private boolean isEditorLocked() {
        return running || localTick > 0 || simulationFailed;
    }

    private boolean guardEditor(String actionName) {
        if (isEditorLocked()) {
            log("\u26a0 Cannot " + actionName + " while the simulation is running, has advanced past tick 0, or has failed. Please reset first.");
            return true;
        }
        return false;
    }

    // Keep a reusable tick-0 snapshot so play and restart reflect the latest editor state.
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
            AppState.setEditorBaselinePath(initialSnapshotPath);
        } catch (Exception ex) {
            log("\u26a0 Could not snapshot initial state: " + ex.getMessage());
        }
    }

    // Initialization
    @FXML
    private void initialize() {
        // Poll fresh simulation logs once per second while the screen is active.
        logPollingTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> fetchLogsAsync())
        );
        logPollingTimeline.setCycleCount(Timeline.INDEFINITE);

        if (intersectionObjectTile != null) {
            intersectionObjectTile.managedProperty().bind(intersectionObjectTile.visibleProperty());
            intersectionObjectTile.setVisible(false);
        }

        // Resume the canvas dimensions chosen on the setup screen.
        canvasWidthTiles  = AppState.getCanvasWidthTiles();
        canvasHeightTiles = AppState.getCanvasHeightTiles();
        restoredOutputState = restorePersistedOutputState();

        // Use listeners instead of property binding so the canvas does not lock SplitPane layout.
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

        viewportStack.setOnDragOver(this::onCanvasDragOver);
        viewportStack.setOnDragDropped(this::onCanvasDragDropped);
        viewportStack.setOnDragExited(e -> {
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            dragHighlightTileX = -1;
            dragHighlightTileY = -1;
            drawViewport();
        });

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

        if (outlinerSearchField != null) {
            outlinerSearchField.textProperty().addListener((obs, o, n) -> filterOutliner(n));
        }

        if (outlinerListView != null) {
            outlinerListView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onOutlinerSelect());
        }

        // Rebuild the engine lazily when the controller is opened from a saved config path.
        engine = AppState.getEngine();
        initialSnapshotPath = AppState.getEditorBaselinePath();
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

        localTick = AppState.getSimulationTick();
        updateRamLabel();

        if (engine != null && engine.getMap() != null) {
            com.openrobotics.map.Map loadedMap = engine.getMap();

            try {
                // Persist the loaded map once so downstream screens can inspect it from the DB.
                MapRecordBuilder recordBuilder = new MapRecordBuilder(loadedMap);
                MapRecord record = recordBuilder.build();
                MapDao.insert(record);
            } catch (Exception e) {
                System.out.println("Error saving map to database: " + e.getMessage());
            }

            // buildBuiltinMapInCanvas and loaded configs already sit at their correct absolute tile positions within the map. Adding a centering offset on top
            // Use map dimensions directly and set offsets to zero.
            canvasWidthTiles  = loadedMap.getWidth();
            canvasHeightTiles = loadedMap.getHeight();
            entityOffsetTileX = 0;
            entityOffsetTileY = 0;
            if (canvasSizeLabel != null) {
                canvasSizeLabel.setText("Canvas Size: " + canvasWidthTiles + "×" + canvasHeightTiles + " Tiles");
            }

            refreshIntersectionObjectTileVisibility();
            populateOutliner();
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("Loaded " + loadedMap.getEntities().size() + " objects");
            }
            if (!restoredOutputState) {
                log("Loaded simulation with " + loadedMap.getEntities().size() + " entities. Press \u25b6 to start.");
            }
            drawViewport();
        } else {
            refreshIntersectionObjectTileVisibility();
            if (viewportStatusLabel != null) {
                viewportStatusLabel.setText("No config loaded");
            }
            if (!restoredOutputState) {
                log("No configuration loaded. Go to Setup \u2192 Load Config first.");
            }
        }

        IconLoader.preloadAllIcons();
        // Preserve the load-state message; only show the default tip in the empty state.
        if (engine == null || engine.getMap() == null) {
            updateSelectionLabel();
        }
        startTipRotation();

        if (editModeLabel    != null) editModeLabel.setText("Edit mode");
        if (viewportModeLabel != null) viewportModeLabel.setText("Right-click to pan, left-click to select");
        if (tipLabel != null) tipLabel.setText("TIP: " + ViewportTips.nextTip());

        if (playBtn  != null) playBtn.setStyle("");
        if (pauseBtn != null) pauseBtn.setStyle("");

        if (mainSplitPane != null && mainSplitPane.getWidth() > 0) {
            mainSplitPane.setDividerPosition(0, 230.0 / mainSplitPane.getWidth());
        }

        if (!restoredOutputState) {
            log("Simulation screen ready. Drag an object from the panel into the viewport.");
        }
    }

    // Viewport Rendering
    private void centerViewportOnCanvas() {
        double tileSize = 32.0 * zoom;
        viewOffsetX = (warehouseCanvas.getWidth()  - canvasWidthTiles  * tileSize) / 2.0;
        viewOffsetY = (warehouseCanvas.getHeight() - canvasHeightTiles * tileSize) / 2.0;
    }

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

    private void drawEntities(GraphicsContext gc) {
        if (engine == null || engine.getMap() == null) return;
        double tileSize = 32 * zoom;
        double pad = Math.max(1.0, tileSize * 0.06);

        for (MapEntity entity : engine.getMap().getEntities()) {
            double ex = entity.getPosition().getX();
            double ey = entity.getPosition().getY();

            // Interpolate robot sprites between ticks so movement appears continuous.
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
                gc.setStroke(Color.web("#1a743f"));
                gc.setLineWidth(Math.max(2.5, tileSize * 0.09));
                double inset = Math.max(1.5, tileSize * 0.04);
                gc.strokeRect(sx + inset, sy + inset, tileSize - 2 * inset, tileSize - 2 * inset);
                gc.setStroke(INTERSECTION_STROKE_COLOR);
                gc.setLineWidth(Math.max(1.5, tileSize * 0.08));
            }
        }
    }

    // Match engine-loaded entities against the closest available sidebar icon.
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

    // Map Bounds
    // Clamp editor placement against the actual map bounds, not just the visible canvas border.
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

        // Build the tile occupancy as if the candidate were already placed there.
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

    // Sidebar Drag and Drop
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

        // Snapshot only the icon, not the full labeled button.
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

    private void onCanvasDragOver(DragEvent e) {
        if (running) { e.consume(); return; }
        if (e.getDragboard().hasString()) {
            e.acceptTransferModes(TransferMode.COPY);
            double tileSize = 32 * zoom;
            int tx = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
            int ty = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);
            // Redraw only when the highlighted tile changes.
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
                    // Intersection markers have their own placement rules and toggle semantics.
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
            // Existing markers can be removed in place, but new ones cannot sit on active robot/station tiles.
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
        pushAction(new IntersectionToggleAction(new Vector2D(tx, ty), !alreadyMarked));
        return true;
    }

    private MapEntity createEntityFromType(String type, int x, int y, String name) {
        java.util.UUID id = java.util.UUID.randomUUID();
        com.openrobotics.map.Vector2D pos = new com.openrobotics.map.Vector2D(x, y);
        String upperType = type.toUpperCase();
        if ("ROBOT".equals(upperType)) {
            Robot robot = new Robot(id, name, pos);
            // Fresh robots start with the same defaults used by setup-generated configs.
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

    // Viewport Interaction
    private void onViewportMousePressed(MouseEvent e) {
        viewportStack.requestFocus();
        lastMouseX = e.getX();
        lastMouseY = e.getY();
        viewportMouseX = e.getX();
        viewportMouseY = e.getY();

        // While assigning rack dropoffs, the next primary click is interpreted as a station picker.
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
            // Intersections get first priority so a marker can be dragged even when a tile also contains an entity.
            Vector2D intersectionHit = intersectionAtScreenPos(e.getX(), e.getY());
            if (intersectionHit != null) {
                selectIntersection(intersectionHit);
                draggingOnCanvas = null;
                dragStartPosition = null;
                dragStartIntersection = intersectionHit;
                dragIntersectionOrigin = intersectionHit;
            } else {
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
                    dragStartIntersection = null;
                }
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            draggingOnCanvas = null;
            dragStartPosition = null;
            dragStartIntersection = null;
        }

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
                // Update the entity live while dragging so the viewport previews the final placement.
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
        } else if (dragStartIntersection != null && e.getButton() == MouseButton.PRIMARY) {
            if (isEditorLocked()) {
                dragStartIntersection = null;
            } else {
                double tileSize = 32 * zoom;
                int newTX = clampTileX((int) Math.floor((e.getX() - viewOffsetX) / tileSize) - entityOffsetTileX);
                int newTY = clampTileY((int) Math.floor((e.getY() - viewOffsetY) / tileSize) - entityOffsetTileY);
                Vector2D newPos = new Vector2D(newTX, newTY);
                if (!newPos.equals(dragStartIntersection)) {
                    // Moving an intersection is modeled as toggling off the old tile and on the new tile.
                    engine.toggleTrafficRuleIntersection(
                            (int) dragStartIntersection.getX(),
                            (int) dragStartIntersection.getY());
                    engine.toggleTrafficRuleIntersection(newTX, newTY);
                    selectedIntersection = newPos;
                    dragStartIntersection = newPos;
                    if (viewportStatusLabel != null)
                        viewportStatusLabel.setText(
                                "dragging(INTERSECTION)  →  (" + newTX + ", " + newTY + ")");
                    drawViewport();
                }
            }
        } else if (e.getButton() == MouseButton.SECONDARY) {
            viewOffsetX += dx;
            viewOffsetY += dy;
            drawViewport();
        }

        lastMouseX = e.getX();
        lastMouseY = e.getY();

        updateRamLabel();
    }

    private void onViewportMouseReleased(MouseEvent e) {
        if (draggingOnCanvas != null) {
            com.openrobotics.map.Vector2D endPos = draggingOnCanvas.getPosition();
            if (dragStartPosition != null && !dragStartPosition.equals(endPos)) {
                // Only create undo history when the entity actually changed tiles.
                pushAction(new MoveAction(draggingOnCanvas, dragStartPosition, endPos));
                syncTaskPositions(dragStartPosition, endPos);
            }
            log("Moved " + draggingOnCanvas.getName()
                    + " to tile (" + (int)endPos.getX() + ", " + (int)endPos.getY() + ").");
            draggingOnCanvas = null;
            dragStartPosition = null;
            dragStartIntersection = null;
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
            if (viewportModeLabel   != null) viewportModeLabel.setText("right-click to pan, left-click to select");

            persistEditorChanges();
        }

        if (dragStartIntersection != null) {
            if (dragIntersectionOrigin != null && !dragIntersectionOrigin.equals(dragStartIntersection)) {
                pushAction(new IntersectionMoveAction(dragIntersectionOrigin, dragStartIntersection));
            }
            log("Moved INTERSECTION to tile ("
                    + (int)dragStartIntersection.getX() + ", "
                    + (int)dragStartIntersection.getY() + ").");
            dragStartIntersection = null;
            dragIntersectionOrigin = null;
            if (viewportStatusLabel != null) viewportStatusLabel.setText("");
        }

        updateRamLabel();
    }

    // Hit Testing
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

        for (Vector2D intersection : engine.getTrafficRuleIntersections()) {
            double px = viewOffsetX + (intersection.getX() + entityOffsetTileX) * tileSize;
            double py = viewOffsetY + (intersection.getY() + entityOffsetTileY) * tileSize;
            if (sx >= px && sx < px + tileSize && sy >= py && sy < py + tileSize) {
                return intersection;
            }
        }
        return null;
    }

    // Selection and Properties
    private void deleteSelected() {
        if (selectedIntersection != null) {
            Vector2D deletedIntersection = selectedIntersection;
            if (engine != null && engine.toggleTrafficRuleIntersection(
                    deletedIntersection.getX(), deletedIntersection.getY())) {
                log("Deleted INTERSECTION at tile (" + deletedIntersection.getX() + ", " + deletedIntersection.getY() + ").");
                pushAction(new IntersectionToggleAction(deletedIntersection, false));
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
        if (selectedIntersection != null) {
            // Intersections are positional markers — copy just arms the paste target
            clipboardIntersection = selectedIntersection;
            clipboardEntity = null;
            log("Copied INTERSECTION at tile (" + selectedIntersection.getX() + ", " + selectedIntersection.getY() + ").");
            return;
        }
        if (selectedEntity == null) return;
        clipboardEntity = selectedEntity;
        clipboardIntersection = null;
        log("Copied " + clipboardEntity.getName() + ".");
    }

    private void pasteClipboard() {
        if (clipboardIntersection != null && engine != null) {
            if (guardEditor("paste")) return;
            // Offset pasted markers by one tile so repeated paste stays visible.
            int newX = clampTileX((int) clipboardIntersection.getX() + 1);
            int newY = clampTileY((int) clipboardIntersection.getY() + 1);
            engine.toggleTrafficRuleIntersection(newX, newY);
            selectedIntersection = new Vector2D(newX, newY);
            populateOutliner();
            drawViewport();
            log("Pasted INTERSECTION at tile (" + newX + ", " + newY + ").");
            pushAction(new IntersectionToggleAction(new Vector2D(newX, newY), true));
            return;
        }
        if (clipboardEntity == null || engine == null || engine.getMap() == null) return;
        if (guardEditor("paste")) return;
        // Offset copied entities by one tile, matching the intersection paste behavior.
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
                // Mirror viewport selection into the outliner when the entity is currently visible there.
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
        // Commit rename on text updates so tests and direct editing behave the same way.
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
            if (newVal == null || oldVal == null || newVal == (int) entity.getPosition().getX()) return;
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
            if (newVal == null || oldVal == null || newVal == (int) entity.getPosition().getY()) return;
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
            // Robot properties expose the live navigation/sensor configuration used by the engine.
            HBox algoBox = new HBox(8);
            algoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            ComboBox<String> algoCombo = new ComboBox<>(
                    FXCollections.observableArrayList("GREEDY", "BUG", "RTA_STAR"));
            algoCombo.setPrefWidth(120);
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

            HBox batteryBox = new HBox(8);
            batteryBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            float maxBattery = robot.getConfig().batteryCapacity;

            Spinner<Double> batterySpinner = new Spinner<>(
                    new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, (double) maxBattery, (double) robot.getBattery(), 1.0));
            batterySpinner.setPrefWidth(90);
            batterySpinner.setEditable(true);

            // Restrict input to positive digits and a single decimal point
            batterySpinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
                String newText = change.getControlNewText();
                if (newText.matches("\\d*(\\.\\d*)?")) {
                    return change;
                }
                return null; // Reject the change
            }));

            // Force spinner to commit text value when the user clicks away
            batterySpinner.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) {
                    batterySpinner.increment(0);
                }
            });

            batterySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || oldVal == null || newVal.floatValue() == robot.getBattery()) return;
                if (guardEditor("change battery")) {
                    // Revert spinner if editor is locked
                        Platform.runLater(() -> batterySpinner.getValueFactory().setValue(oldVal));
                    return;
                }
                    float newBat = newVal.floatValue();
                    float oldBat = oldVal.floatValue();

                    robot.setBattery(newBat);
                drawViewport();
                pushAction(new BatteryChangeAction(robot, oldBat, newBat));
            });

            batteryBox.getChildren().addAll(new Label("Battery:"), batterySpinner);
            propertiesPanel.getChildren().add(batteryBox);

            Label stateLabel = new Label("State: " + robot.getState());
            stateLabel.setStyle("-fx-text-fill: #666;");
            propertiesPanel.getChildren().add(stateLabel);
        } else if (entity instanceof Station) {
            Label stationProps = new Label("Station configuration");
            propertiesPanel.getChildren().add(stationProps);
        } else if (entity instanceof Rack rack) {
            // Rack-specific controls only matter when the run uses manual task assignment.
            boolean globalManual = engine != null && engine.isManualTaskAssignment();

            if (globalManual) {
                HBox boxCountBox = new HBox(8);
                boxCountBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Spinner<Integer> boxCountSpinner = new Spinner<>(
                        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99, rack.getBoxCount()));
                boxCountSpinner.setPrefWidth(80);
                boxCountSpinner.setEditable(true);

                // Restrict input to positive integers only (no decimals, no negatives)
                boxCountSpinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
                    if (change.getControlNewText().matches("\\d*")) {
                        return change;
                    }
                    return null; // Reject non-digit characters
                }));

                // Force spinner to commit text value when the user clicks away
                boxCountSpinner.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) {
                        boxCountSpinner.increment(0);
                    }
                });

                boxCountSpinner.valueProperty().addListener((obs, oldV, newV) -> {
                    if (newV == null || oldV == null || newV.equals(rack.getBoxCount())) return;

                    if (guardEditor("change box count")) {
                        // Revert spinner if editor is locked
                        javafx.application.Platform.runLater(() -> boxCountSpinner.getValueFactory().setValue(oldV));
                        return;
                    }

                    rack.setBoxCount(newV);
                    persistEditorChanges();
                });

                boxCountBox.getChildren().addAll(new Label("Number of tasks:"), boxCountSpinner);
                propertiesPanel.getChildren().add(boxCountBox);

                HBox manualBox = new HBox(8);
                manualBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                CheckBox manualCheck = new CheckBox("Manual dropoff assignment");
                manualCheck.setSelected(rack.isManualDropoffAssignment());

                VBox dropoffArrayBox = new VBox(4);
                dropoffArrayBox.setVisible(rack.isManualDropoffAssignment());
                dropoffArrayBox.setManaged(rack.isManualDropoffAssignment());

                manualCheck.selectedProperty().addListener((obs, oldV, newV) -> {
                    if (newV == rack.isManualDropoffAssignment()) return;
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

                renderRackDropoffArray(rack, dropoffArrayBox);
                propertiesPanel.getChildren().add(dropoffArrayBox);
            }
        }
    }

    private void renderRackDropoffArray(Rack rack, VBox container) {
        container.getChildren().clear();

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

        // Resolve stored UUIDs back to live delivery stations for display.
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
            // Reuse the main viewport click handler for the actual station assignment.
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

    private void updateSelectionLabel() {
        if (viewportStatusLabel != null) {
            String tip = ViewportTips.getRandomSelectionTip();
            viewportStatusLabel.setText("Select — " + tip);
        }
    }

    private void startTipRotation() {
        tipRotationLoop = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            if (tipLabel != null) {
                tipLabel.setText("TIP: " + ViewportTips.nextTip());
            }
        }));
        tipRotationLoop.setCycleCount(Animation.INDEFINITE);
        tipRotationLoop.play();
    }

    // Add Object and Outliner
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

    private void populateOutliner() {
        if (outlinerListView == null || engine == null || engine.getMap() == null || engine.getMap().getEntities() == null) return;
        String filter = (outlinerSearchField != null && outlinerSearchField.getText() != null)
                ? outlinerSearchField.getText().toLowerCase() : "";
        List<String> newItems = new ArrayList<>();
        List<Object> newBacking = new ArrayList<>();
        // Keep entity rows first so editor selections stay predictable.
        for (MapEntity e : engine.getMap().getEntities()) {
            String icon  = (e instanceof Robot)    ? "\ud83e\udd16 "    // robot
                    : (e instanceof Rack)     ? "\ud83d\udce6 "         // rack/shelf
                    : (e instanceof ChargingStation)  ? "\u26a1 "       // charging station
                    : (e instanceof DeliveryStation) ? "\uD83C\uDFC1 "             // delivery station
                    : (e instanceof Obstacle) ? "\ud83e\uddf1 "         // wall/obstacle
                    : "\u25ab ";                                        // generic entity
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

        // Skip redundant ListView updates to avoid unnecessary layout work.
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

    // Playback Controls
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

        logPollingTimeline.play();

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
                updateRamLabel();
            }
        } else {
            // Ensure a tick-0 baseline exists even if the editor has not been modified yet.
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

            // Manual mode requires every participating rack to have at least one valid dropoff.
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

            // Seed the dispatcher on first play if setup did not pre-populate tasks.
            if (engine.getDispatcher().getAllQueuedTasks().isEmpty() && engine.getMap() != null) {
                List<Task> generated;
                if (engine.isManualTaskAssignment()) {
                    generated = com.openrobotics.task.TaskGenerator.generateRandomTasks(
                            engine.getMap(), Integer.MAX_VALUE, engine.getSeed());
                } else {
                    generated = com.openrobotics.task.TaskGenerator.generateAutomaticTasks(
                            engine.getMap(), engine.getMaxTasks(), engine.getSeed());

                    // No task assignments could be generated due to invalid map configuration
                    if (generated.isEmpty()) {
                        log("\u26a0 No tasks could be generated.");
                        engine.setSimulationError(SimulationError.INVALID_MAP_CONFIGURATION);
                        handleSimulationFailure();
                        return;
                    }
                }
                if (!generated.isEmpty()) {
                    engine.getDispatcher().addTasks(generated);
                    log("Auto-generated " + generated.size() + " tasks from map racks and delivery stations.");
                }
            }

            // Logging simulation start event
            SimulationRunRecordBuilder simRunRecordBuilder = new SimulationRunRecordBuilder(engine);
            SimulationRunRecord record = simRunRecordBuilder.buildSimulationStartRecord();
            Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, record);

            startLoop();
            log("Simulation started.");
            updateRamLabel();
        }
    }

    @FXML
    private void onPause() {
        logPollingTimeline.stop();

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
        updateRamLabel();
    }

    @FXML
    private void onRestart() {
        String earlyReloadPath = resolveReloadPath();
        if (localTick == 0 && !running && !simulationFailed && earlyReloadPath == null) {
            log("Already at tick 0. Nothing to reset.");
            return;
        }
        if (animTimeline != null) { animTimeline.stop(); animTimeline = null; }
        animating = false;
        animationProgress = 0.0;
        prevRobotPositions.clear();
        selectedEntity = null;
        // A restart returns to the saved baseline, so editor history from the previous run no longer applies.
        undoStack.clear();
        redoStack.clear();
        onStop();
        localTick = 0;
        simulationFailed = false;
        lastSeenLogId = 0;
        AppState.setSimulationTick(0);
        AppState.setSimulationLogCursor(0);
        // Reload the latest editor baseline rather than the original config file.
        String reloadPath = resolveReloadPath();
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

        engine.reset();

        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK 0");
        if (simProgressBar != null) simProgressBar.setProgress(0);
        if (simStatusLabel != null) {
            simStatusLabel.setText("READY");
            simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
        }
        log("Simulation reset.");
        updateRamLabel();
        populateOutliner();
        drawViewport();

        if (logArea != null) {
            System.out.println("[SimulationController] Clearing log area on simulation reset.");
            logArea.clear();
        }
        AppState.setSimulationLogText("");
        AppState.setSimulationLogCursor(0);
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
        updateRamLabel();

        Logger.flushRobotEvents();
        fetchLogsAsync();
    }

    @FXML private void onSpeed1() { setSpeed(1); log("Speed set to ×1."); }
    @FXML private void onSpeed2() { setSpeed(2); log("Speed set to ×2."); }
    @FXML private void onSpeed3() { setSpeed(3); log("Speed set to ×3."); }
    @FXML private void onSpeed10() { setSpeed(10); log("Speed set to ×10."); }

    // Simulation Loop
    private void setSpeed(int factor) {
        speedFactor = factor;
        if (running && !paused) startLoop();
    }

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
        logPollingTimeline.stop();

        if (simLoop != null) {
            simLoop.stop();
            simLoop = null;
        }

        System.out.println("[SimulationController] Flushing logs and fetching remaining logs on stop...");
        Logger.flushRobotEvents();
        fetchLogsSync();
    }

    @Override
    public void cleanup() {
        shutdown();
        stopLoop();
        if (AppState.hasEngine()) {
            persistOutputState();
        }
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
            // Capture pre-tick robot positions so the renderer can tween to the new state.
            prevRobotPositions.clear();
            for (Robot robot : engine.getRobots()) {
                prevRobotPositions.put(robot.getId(), new com.openrobotics.map.Vector2D(
                        robot.getPosition().getX(), robot.getPosition().getY()));
            }
        }

        if (!engine.tick()) {
            if (engine.getSimulationError() != SimulationError.NONE) {
                handleSimulationFailure();
            } else {
                handleSimulationComplete();
            }

            return;
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
        log("Simulation failed: " + engine.getSimulationErrorMessage());
        log("Simulation failed at TICK " + localTick + ".");
    }

    private void startAnimation() {
        if (animTimeline != null) animTimeline.stop();
        animating = true;
        animationProgress = 0.0;
        animTimeline = new Timeline();
        final int totalFrames = ANIMATION_FRAMES;
        // Spread each logical tick across a short frame sequence for smoother robot movement.
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

    // Viewport Controls
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
            // Restore the saved divider position after the current layout pass.
            Platform.runLater(() -> mainSplitPane.setDividerPosition(0, savedSidebarDivider));
        } else {
            // Save the divider before collapsing the sidebar completely.
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
            // Restore the saved divider position after the current layout pass.
            Platform.runLater(() -> viewportConsoleSplit.setDividerPosition(0, savedConsoleDivider));
        } else {
            // Save the divider before collapsing the console completely.
            if (viewportConsoleSplit.getDividerPositions().length > 0) {
                savedConsoleDivider = viewportConsoleSplit.getDividerPositions()[0];
            }
            Platform.runLater(() -> viewportConsoleSplit.setDividerPosition(0, 1.0));
        }
    }

    // Console and Configuration
    @FXML
    private void onClearConsole() {
        if (consoleArea != null) consoleArea.clear();
        AppState.setSimulationConsoleText("");
    }

    @FXML
    private void onSaveConfig() {
        ScreenNavigator.openDialog(ScreenNavigator.DIALOG_SAVE_CONFIG, "Save Configuration", ctrl -> {
            if (ctrl instanceof SaveConfigController scc) {
                SimulationEngine engine = AppState.getEngine();
                String runName = engine != null ? engine.getRunName() : null;
                if (runName != null && !runName.isEmpty()) {
                    scc.setDefaultFileName(runName);
                }
            }
        });
    }

    @FXML
    private void onLoadConfig() {
        javafx.fxml.FXMLLoader loader = ScreenNavigator.openDialog(
                ScreenNavigator.DIALOG_LOAD_CONFIG, "Load Configuration");
        Object ctrl = loader.getController();
        if (!(ctrl instanceof LoadConfigController lcc)) return;
        java.io.File file = lcc.getSelectedFile();
        if (file == null) return;

        // Clear transient editor state before rebuilding the engine from the chosen file.
        stopLoop();
        running = false;
        paused = false;
        localTick = 0;
        AppState.setSimulationTick(0);
        selectedEntity = null;
        undoStack.clear();
        redoStack.clear();
        clearPersistedOutputState();

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
        if (consoleArea != null) {
            consoleArea.appendText(message + "\n");
            AppState.setSimulationConsoleText(consoleArea.getText());
        } else {
            String existing = AppState.getSimulationConsoleText();
            AppState.setSimulationConsoleText((existing == null ? "" : existing) + message + "\n");
        }
    }

    // Database Logging
    /**
     * Fetches simulation logs on a background thread to avoid blocking the UI.
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
            // onSucceeded runs on the FX thread, so it is safe to append directly to the log area here.
            List<SimLogRecord> logs = (List<SimLogRecord>) task.getValue();
            long start = System.currentTimeMillis();
            updateLogsArea(logs);
            System.out.println("[SimulationController] Updated sim log area in " + (System.currentTimeMillis() - start) + " ms.");
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
        });

        executor.submit(task);
    }

    /**
     * Fetches simulation logs synchronously and updates the log area.
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

    // Append only the newly fetched log rows and advance the database cursor.
    private void updateLogsArea(List<SimLogRecord> logs) {
        if (logs == null || logs.isEmpty()) return;

        StringBuilder sb = new StringBuilder(logs.size() * 80);

        for (SimLogRecord log : logs) {
            sb.append(log.toString()).append("\n");
            lastSeenLogId = log.getId();
        }

        if (logArea != null) {
            logArea.appendText(sb.toString());
            AppState.setSimulationLogText(logArea.getText());
        } else {
            String existing = AppState.getSimulationLogText();
            AppState.setSimulationLogText((existing == null ? "" : existing) + sb);
        }
        AppState.setSimulationLogCursor(lastSeenLogId);
    }

    private boolean restorePersistedOutputState() {
        lastSeenLogId = AppState.getSimulationLogCursor();
        localTick = AppState.getSimulationTick();

        boolean restored = false;

        if (tickDisplayLabel != null) tickDisplayLabel.setText("TICK " + localTick);
        if (consoleArea != null && AppState.getSimulationConsoleText() != null) {
            consoleArea.setText(AppState.getSimulationConsoleText());
            restored = restored || !consoleArea.getText().isBlank();
        }
        if (logArea != null && AppState.getSimulationLogText() != null) {
            logArea.setText(AppState.getSimulationLogText());
            restored = restored || !logArea.getText().isBlank();
        }
        return restored;
    }

    private void persistOutputState() {
        if (consoleArea != null) {
            AppState.setSimulationConsoleText(consoleArea.getText());
        }
        if (logArea != null) {
            AppState.setSimulationLogText(logArea.getText());
        }
        AppState.setSimulationLogCursor(lastSeenLogId);
    }

    private void clearPersistedOutputState() {
        if (consoleArea != null) {
            consoleArea.clear();
        }
        if (logArea != null) {
            logArea.clear();
        }
        AppState.setSimulationConsoleText("");
        AppState.setSimulationLogText("");
        AppState.setSimulationLogCursor(0);
        lastSeenLogId = 0;
    }

    // Navigation
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

    @FXML
    private void onToggleShortcutOverlay() {
        boolean nowVisible = !shortcutOverlay.isVisible();
        shortcutOverlay.setVisible(nowVisible);
        shortcutOverlay.setManaged(nowVisible);
    }

    // Editor Persistence
    // Keep queued tasks aligned when their source entity moves in the editor.
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
            populateOutliner();
        }
    }

    // Save the latest pre-run editor state so play and restart reload the current layout.
    private void persistEditorChanges() {
        if (engine == null || running) return;
        try {
            // Reuse the existing snapshot file when possible.
            String savePath = resolveReloadPath();
            if (savePath == null) {
                java.io.File tmp = java.io.File.createTempFile("openrobotics_editor_", ".json");
                tmp.deleteOnExit();
                savePath = tmp.getAbsolutePath();
                initialSnapshotPath = savePath;
            }
            // Keep restart/play anchored to the latest editable layout rather than the original import.
            engine.configSaving(savePath);
            initialSnapshotPath = savePath;
            AppState.setEditorBaselinePath(savePath);
        } catch (Exception ex) {
            log("\u26a0 Could not auto-save editor changes: " + ex.getMessage());
        }
    }

    private String resolveReloadPath() {
        if (initialSnapshotPath != null && !initialSnapshotPath.isBlank()) {
            return initialSnapshotPath;
        }
        if (AppState.hasEditorBaselinePath()) {
            return AppState.getEditorBaselinePath();
        }
        return AppState.getConfigPath();
    }

    private void updateRamLabel() {
        if (ramLabel != null) {
            long usedKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024;
            ramLabel.setText("RAM: " + usedKb + " KB");
        }
    }

    // Shutdown
    /**
     * Performs controller shutdown cleanup before the application closes.
     */
    public void shutdown() {
        // Stop polling first so no new background work is queued during shutdown.
        if (logPollingTimeline != null) {
            logPollingTimeline.stop();
        }

        // Interrupt any in-flight log fetches after polling is stopped.
        executor.shutdownNow();

        Logger.flushRobotEvents();
    }
}