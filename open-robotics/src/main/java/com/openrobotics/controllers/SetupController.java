package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.map.entities.station.Station;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskGenerator;
import com.openrobotics.util.IconLoader;
import com.openrobotics.util.ScreenNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for {@code SetupScreen.fxml}.
 * Manages simulation configuration input, preview rendering, config-file loading, and transition
 * into the editor flow (§4.1.2).
 */
public class SetupController {

    // Map Settings
    @FXML private ComboBox<String> mapCombo;
    @FXML private HBox mapSeedRow;
    @FXML private TextField        randomSeedField;

    // Coordination Policy
    @FXML private ComboBox<String> policyCombo;
    @FXML private Spinner<Integer> reservationKSpinner;

    // Task Settings
    @FXML private RadioButton      autoTaskRadio;
    @FXML private RadioButton      manualTaskRadio;
    @FXML private VBox             maxTasksGroup;
    @FXML private TextField        maxTasksField;
    @FXML private TextField        workloadSeedField;

    // Robot Settings
    @FXML private TextField        batteryCapacityField;
    @FXML private TextField        lowBatteryField;
    @FXML private TextField        chargePerTickField;
    @FXML private TextField        energyPerMoveField;

    // Run Settings
    @FXML private TextField maxTicksField;

    @FXML private TextField runNameField;

    // Preview
    @FXML private Spinner<Integer> canvasWidthSpinner;
    @FXML private Spinner<Integer> canvasHeightSpinner;
    @FXML private Canvas    previewCanvas;
    @FXML private StackPane viewportPreviewStack;
    @FXML private Label     canvasWarnLabel;

    // Status
    @FXML private Label statusLabel;

    // Config Files
    @FXML private ListView<String> configListView;

    // Defaults
    private static final Color INTERSECTION_FILL_COLOR = Color.web("#8C7B38", 0.25);
    private static final Color INTERSECTION_STROKE_COLOR = Color.web("#6A4828");
    private static final String DEFAULT_POLICY        = "NONE";
    private static final int    DEFAULT_RESERVATION_K = 3;
    private static final int    DEFAULT_MAX_TASKS     = 10;
    private static final long   DEFAULT_WORKLOAD_SEED = 42;
    private static final int    DEFAULT_MAX_TICKS     = 30000;
    private static final String DEFAULT_RUN_NAME      = "experiment_1";
    private static final int    DEFAULT_TICK_MS          = 100;  // fixed, not user-configurable
    private static final int    DEFAULT_LOADING_TICKS    = 1;   // fixed, not user-configurable
    private static final int    DEFAULT_UNLOADING_TICKS  = 1;   // fixed, not user-configurable
    private static final float  DEFAULT_BATTERY_CAPACITY = 100.0f;
    private static final float  DEFAULT_LOW_BATTERY      = 20.0f;
    private static final float  DEFAULT_CHARGE_PER_TICK  = 5.0f;
    private static final float  DEFAULT_ENERGY_PER_MOVE  = 1.0f;
    private final String CONFIG_DIRECTORY_PATH = "./configs";

    // Initialization
    @FXML
    private void initialize() {
        mapCombo.setItems(FXCollections.observableArrayList(
                "empty", "baseline_small", "narrow_aisles", "many_intersections", "random_map"));
        mapCombo.getSelectionModel().selectFirst();

        mapSeedRow.setVisible(false);
        mapSeedRow.managedProperty().bind(mapSeedRow.visibleProperty());
        if (canvasWarnLabel != null) {
            canvasWarnLabel.managedProperty().bind(canvasWarnLabel.visibleProperty());
        }
        mapCombo.valueProperty().addListener((obs, o, n) -> {
            boolean isRandom = "random_map".equals(n);
            mapSeedRow.setVisible(isRandom);
            if (n != null) {
                // Switching back to a template invalidates any loaded config-backed engine.
                if (AppState.hasEngine()) {
                    AppState.clear();
                    configListView.getSelectionModel().clearSelection();
                }
                setConfigParamsDisabled(false);
                autoSetCanvasForMap(n);
            }
            refreshPreview();
            checkCanvasConstraint();
        });

        reservationKSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, DEFAULT_RESERVATION_K));

        policyCombo.setItems(FXCollections.observableArrayList(
                "NONE", "TRAFFIC_RULES", "RESERVATION_K"));
        policyCombo.getSelectionModel().select(DEFAULT_POLICY);

        canvasWidthSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5000, AppState.getCanvasWidthTiles()));
        canvasHeightSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5000, AppState.getCanvasHeightTiles()));
        canvasWidthSpinner.valueProperty().addListener((obs, o, n) -> {
            AppState.setCanvasDimensions(n, AppState.getCanvasHeightTiles());
            refreshPreview();
            checkCanvasConstraint();
        });
        canvasHeightSpinner.valueProperty().addListener((obs, o, n) -> {
            AppState.setCanvasDimensions(AppState.getCanvasWidthTiles(), n);
            refreshPreview();
            checkCanvasConstraint();
        });

        ToggleGroup taskModeGroup = new ToggleGroup();
        autoTaskRadio.setToggleGroup(taskModeGroup);
        manualTaskRadio.setToggleGroup(taskModeGroup);
        maxTasksGroup.managedProperty().bind(maxTasksGroup.visibleProperty());
        maxTasksGroup.setVisible(true); // default: automatic
        taskModeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            boolean isManual = newT == manualTaskRadio;
            maxTasksGroup.setVisible(!isManual);
        });

        maxTasksField.setText(String.valueOf(DEFAULT_MAX_TASKS));
        workloadSeedField.setText(String.valueOf(DEFAULT_WORKLOAD_SEED));
        maxTicksField.setText(String.valueOf(DEFAULT_MAX_TICKS));
        runNameField.setText(DEFAULT_RUN_NAME);

        setTextIfPresent(batteryCapacityField, String.valueOf((int) DEFAULT_BATTERY_CAPACITY));
        setTextIfPresent(lowBatteryField, String.valueOf((int) DEFAULT_LOW_BATTERY));
        setTextIfPresent(chargePerTickField, String.valueOf((int) DEFAULT_CHARGE_PER_TICK));
        setTextIfPresent(energyPerMoveField, String.valueOf((int) DEFAULT_ENERGY_PER_MOVE));

        reservationKSpinner.setDisable(true);
        policyCombo.valueProperty().addListener((obs, oldVal, newVal) ->
                reservationKSpinner.setDisable(!"RESERVATION_K".equals(newVal)));

        // Keep the preview canvas synced to the available stack size without forcing layout width.
        viewportPreviewStack.widthProperty().addListener((obs, o, n) -> {
            previewCanvas.setWidth(n.doubleValue());
            refreshPreview();
        });
        viewportPreviewStack.heightProperty().addListener((obs, o, n) -> {
            previewCanvas.setHeight(n.doubleValue());
            refreshPreview();
        });

        onRefreshFileList();

        configListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                File selectedFile = new File(CONFIG_DIRECTORY_PATH, newValue);
                boolean success = loadConfigFile(selectedFile);
                if (success) {
                    // Config-backed maps own their dimensions, so clear the template selection.
                    mapCombo.getSelectionModel().clearSelection();
                    setConfigParamsDisabled(true);
                    refreshPreview();
                    checkCanvasConstraint();
                }
            }
        });

        // Apply input restrictions (prevent negative signs and letters)
        makeIntegerOnly(maxTasksField);
        makeIntegerOnly(maxTicksField);
        makeIntegerOnly(workloadSeedField);
        makeIntegerOnly(randomSeedField);
        makeFloatOnly(batteryCapacityField);
        makeFloatOnly(lowBatteryField);
        makeFloatOnly(chargePerTickField);
        makeFloatOnly(energyPerMoveField);
        makeSpinnerIntegerOnly(reservationKSpinner);

        String initialMap = mapCombo.getValue();
        if (initialMap != null) autoSetCanvasForMap(initialMap);
    }

    // Reset Actions
    @FXML private void onResetMap() {
        AppState.clear();
        configListView.getSelectionModel().clearSelection();
        setConfigParamsDisabled(false);
        mapCombo.getSelectionModel().selectFirst();
        refreshPreview();
        checkCanvasConstraint();
    }
    @FXML private void onResetRandomSeed()   { randomSeedField.setText(""); }
    @FXML private void onResetPolicy()       { policyCombo.getSelectionModel().select(DEFAULT_POLICY); }
    @FXML private void onResetMaxTasks()     { maxTasksField.setText(String.valueOf(DEFAULT_MAX_TASKS)); }
    @FXML private void onResetWorkloadSeed() { workloadSeedField.setText(String.valueOf(DEFAULT_WORKLOAD_SEED)); }
    @FXML private void onResetMaxTicks()     { maxTicksField.setText(String.valueOf(DEFAULT_MAX_TICKS)); }
    @FXML private void onResetRunName()      { runNameField.setText(DEFAULT_RUN_NAME); }
    @FXML private void onResetCanvasTiles()  {
        canvasWidthSpinner.getValueFactory().setValue(16);
        canvasHeightSpinner.getValueFactory().setValue(10);
    }
    @FXML private void onResetReservationK() {
        reservationKSpinner.getValueFactory().setValue(DEFAULT_RESERVATION_K);
    }
    @FXML private void onResetBatteryCapacity()     { setTextIfPresent(batteryCapacityField, String.valueOf((int) DEFAULT_BATTERY_CAPACITY)); }
    @FXML private void onResetLowBatteryThreshold() { setTextIfPresent(lowBatteryField, String.valueOf((int) DEFAULT_LOW_BATTERY)); }
    @FXML private void onResetChargePerTick()       { setTextIfPresent(chargePerTickField, String.valueOf((int) DEFAULT_CHARGE_PER_TICK)); }
    @FXML private void onResetEnergyPerMove()       { setTextIfPresent(energyPerMoveField, String.valueOf((int) DEFAULT_ENERGY_PER_MOVE)); }

    // Preview Rendering
    private void refreshPreview() {
        double vw = previewCanvas.getWidth();
        double vh = previewCanvas.getHeight();
        if (vw <= 0 || vh <= 0) return;

        GraphicsContext gc = previewCanvas.getGraphicsContext2D();

        com.openrobotics.map.Map previewMap = null;

        if (AppState.hasEngine() && AppState.getEngine().getMap() != null) {
            previewMap = AppState.getEngine().getMap();
        } else {
            String mapName = mapCombo.getValue();
            if (mapName != null) previewMap = buildBuiltinMap(mapName);
        }

        int cw = canvasWidthSpinner.getValue() != null ? canvasWidthSpinner.getValue() : AppState.getCanvasWidthTiles();
        int ch = canvasHeightSpinner.getValue() != null ? canvasHeightSpinner.getValue() : AppState.getCanvasHeightTiles();

        // Loaded config previews use fixed map dimensions instead of the editable canvas spinners.
        boolean isConfigFile = AppState.hasEngine() && AppState.getEngine().getMap() == previewMap;
        if (isConfigFile && previewMap != null) {
            cw = previewMap.getWidth();
            ch = previewMap.getHeight();
            setConfigParamsDisabled(true);
        }

        double padding = 16;
        double tileSize = Math.min((vw - 2 * padding) / cw, (vh - 2 * padding) / ch);
        tileSize = Math.max(tileSize, 3);

        double bw = cw * tileSize, bh = ch * tileSize;
        double bx = (vw - bw) / 2.0, by = (vh - bh) / 2.0;

        gc.setFill(Color.web("#A8A598")); gc.fillRect(0, 0, vw, vh);
        gc.setFill(Color.web("#CDCBC3")); gc.fillRect(bx, by, bw, bh);
        gc.setStroke(Color.web("#B0ADA5")); gc.setLineWidth(0.5);
        for (int x = 0; x <= cw; x++) gc.strokeLine(bx + x*tileSize, by, bx + x*tileSize, by + bh);
        for (int y = 0; y <= ch; y++) gc.strokeLine(bx, by + y*tileSize, bx + bw, by + y*tileSize);
        gc.setStroke(Color.web("#5D5B54")); gc.setLineWidth(2.0);
        gc.strokeRect(bx, by, bw, bh);

        if (previewMap != null && !previewMap.getEntities().isEmpty()) {
            int[] bounds = computeEntityBounds(previewMap);
            int minX = bounds[0], minY = bounds[1], maxX = bounds[2], maxY = bounds[3];
            int contentW = maxX - minX + 1, contentH = maxY - minY + 1;
            int tileOffX = (cw - contentW) / 2 - minX;
            int tileOffY = (ch - contentH) / 2 - minY;

            drawPreviewEntities(gc, previewMap, bx, by, tileSize, tileOffX, tileOffY);
            drawTrafficRuleIntersections(gc, previewMap, bx, by, tileSize, tileOffX, tileOffY);
        }
    }

    private void drawPreviewEntities(GraphicsContext gc, com.openrobotics.map.Map map,
                                     double ox, double oy, double tileSize,
                                     int tileOffX, int tileOffY) {
        double pad = Math.max(1.0, tileSize * 0.06);

        // Template maps preview layout only; robot markers are shown for loaded configs.
        boolean showRobots = AppState.hasEngine() && AppState.getEngine().getMap() == map;

        for (MapEntity entity : map.getEntities()) {
            if (entity instanceof Robot && !showRobots) continue;

            double sx = ox + (tileOffX + entity.getPosition().getX()) * tileSize;
            double sy = oy + (tileOffY + entity.getPosition().getY()) * tileSize;
            Image icon = resolvePreviewIcon(entity);
            boolean wallLike = entity instanceof Obstacle;
            if (icon != null && !icon.isError()) {
                if (wallLike) gc.drawImage(icon, sx, sy, tileSize, tileSize);
                else          gc.drawImage(icon, sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad);
            } else if (entity instanceof Robot) {
                // Fallback robot marker when the preview icon is unavailable.
                gc.setFill(Color.web("#4D4B45"));
                gc.fillRoundRect(sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad, 4, 4);
                double r = Math.max(2.0, tileSize * 0.15);
                gc.setFill(Color.web("#599068"));
                gc.fillOval(sx + tileSize - r*2 - pad, sy + pad, r*2, r*2);
            } else if (entity instanceof Rack) {
                gc.setFill(Color.web("#8D8A7F")); gc.fillRect(sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad);
            } else if (entity instanceof ChargingStation) {
                gc.setFill(Color.web("#599068")); gc.fillRect(sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad);
            } else if (entity instanceof DeliveryStation) {
                gc.setFill(Color.web("#599068")); gc.fillRect(sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad);
            } else if (entity instanceof Obstacle) {
                gc.setFill(Color.web("#5D5B54")); gc.fillRect(sx, sy, tileSize, tileSize);
            }
        }
    }

    private void drawTrafficRuleIntersections(GraphicsContext gc, com.openrobotics.map.Map map, double ox, double oy, double tileSize, int tileOffX, int tileOffY) {
        if (!AppState.hasEngine() || !AppState.getEngine().usesTrafficRulesPolicy()) return;
        double markerInset = Math.max(3.0, tileSize * 0.22);

        Image intersectionIcon = IconLoader.getIcon("INTERSECTION");

        gc.setStroke(INTERSECTION_STROKE_COLOR);
        gc.setLineWidth(Math.max(1.5, tileSize * 0.08));

        for (Vector2D intersection : AppState.getEngine().getTrafficRuleIntersections()) {
            double sx = ox + (tileOffX + intersection.getX()) * tileSize;
            double sy = oy + (tileOffY + intersection.getY()) * tileSize;

            if (intersectionIcon != null && !intersectionIcon.isError()) {
                gc.drawImage(intersectionIcon, sx, sy, tileSize, tileSize);
            } else {
                double centerInset = Math.max(2.0, tileSize * 0.38);
                gc.setFill(INTERSECTION_FILL_COLOR);
                gc.fillOval(sx + markerInset, sy + markerInset, tileSize - 2 * markerInset, tileSize - 2 * markerInset);
                gc.strokeLine(sx + centerInset, sy + tileSize / 2.0, sx + tileSize - centerInset, sy + tileSize / 2.0);
                gc.strokeLine(sx + tileSize / 2.0, sy + centerInset, sx + tileSize / 2.0, sy + tileSize - centerInset);
            }
        }
    }

    private Image resolvePreviewIcon(MapEntity entity) {
        List<String> candidates = new ArrayList<>();
        if (entity instanceof Robot)            candidates.add("ROBOT");
        if (entity instanceof ChargingStation) candidates.add("CHARGER");
        if (entity instanceof DeliveryStation) { candidates.add("STATION"); candidates.add("DOCK"); }
        if (entity instanceof Station)         candidates.add("STATION");
        if (entity instanceof Rack)            { candidates.add("SHELF"); candidates.add("RACK"); }
        if (entity instanceof Obstacle)        { candidates.add("WALL"); candidates.add("OBSTACLE"); }
        for (String k : candidates) {
            Image img = IconLoader.getIcon(k);
            if (img != null && !img.isError()) return img;
        }
        return null;
    }

    // Built-in Maps
    private com.openrobotics.map.Map buildBuiltinMap(String name) {
        return switch (name) {
            case "empty"              -> null;
            case "baseline_small"     -> buildBaselineSmall();
            case "narrow_aisles"      -> buildNarrowAisles();
            case "many_intersections" -> buildManyIntersections();
            default                   -> null;
        };
    }

    // Builds the 16x10 baseline warehouse template.
    private com.openrobotics.map.Map buildBaselineSmall() {
        final int W = 16, H = 10;
        com.openrobotics.map.Map m = new com.openrobotics.map.Map(W, H);

        // Boundary walls
        for (int x = 0; x < W; x++) {
            m.addEntity(new Obstacle("wall_top_" + x, new Vector2D(x, 0)));
            m.addEntity(new Obstacle("wall_bot_" + x, new Vector2D(x, H - 1)));
        }
        final int chargerRow = 4;

        // Four 2-wide units at cols {2-3}, {5-6}, {8-9}, {11-12}.
        // Row blocks: rows 1-3 (above main aisle) and 5-7 (below).
        // Vertical aisles at cols 1, 4, 7, 10, 13-14 (robot highways).
        int[] unitStarts = {2, 5, 8, 11};
        int[][] rowBlocks = {{1, 3}, {5, 7}};
        int id = 0;
        for (int us : unitStarts)
            for (int[] rb : rowBlocks)
                for (int c = us; c <= us + 1; c++)
                    for (int r = rb[0]; r <= rb[1]; r++)
                        m.addEntity(new Rack("rack_" + id++, new Vector2D(c, r)));

        m.addEntity(new ChargingStation("charger",  new Vector2D(0, chargerRow)));
        m.addEntity(new DeliveryStation("delivery", new Vector2D(W - 1, chargerRow)));
        return m;
    }

    // Builds the 22x14 narrow-aisles warehouse template.
    private com.openrobotics.map.Map buildNarrowAisles() {
        final int W = 22, H = 14;
        com.openrobotics.map.Map m = new com.openrobotics.map.Map(W, H);

        for (int x = 0; x < W; x++) {
            m.addEntity(new Obstacle("wall_top_" + x, new Vector2D(x, 0)));
            m.addEntity(new Obstacle("wall_bot_" + x, new Vector2D(x, H - 1)));
        }
        final int chargerRow = 6;

        // Six 2-wide units at cols {2-3},{5-6},{8-9},{11-12},{14-15},{17-18}.
        // Tall row blocks: 1-5 (above main aisle) and 8-12 (below).
        // Vertical aisles at cols 4, 7, 10, 13, 16.
        // Extra horizontal lower aisle at row 7.
        int[] unitStarts = {2, 5, 8, 11, 14, 17};
        int[][] rowBlocks = {{1, 5}, {8, 12}};
        int id = 0;
        for (int us : unitStarts)
            for (int[] rb : rowBlocks)
                for (int c = us; c <= us + 1; c++)
                    for (int r = rb[0]; r <= rb[1]; r++)
                        m.addEntity(new Rack("rack_" + id++, new Vector2D(c, r)));

        m.addEntity(new ChargingStation("charger",  new Vector2D(0, chargerRow)));
        m.addEntity(new DeliveryStation("delivery", new Vector2D(W - 1, chargerRow)));
        return m;
    }

    // Builds the 26x18 dense-intersection warehouse template.
    private com.openrobotics.map.Map buildManyIntersections() {
        final int W = 26, H = 18;
        com.openrobotics.map.Map m = new com.openrobotics.map.Map(W, H);

        for (int x = 0; x < W; x++) {
            m.addEntity(new Obstacle("wall_top_" + x, new Vector2D(x, 0)));
            m.addEntity(new Obstacle("wall_bot_" + x, new Vector2D(x, H - 1)));
        }
        final int chargerRow = 8;

        // Seven 2-wide units at cols {2-3},{5-6},{8-9},{11-12},{14-15},{17-18},{20-21}.
        // Four short 2-tall row blocks: rows 1-2, 4-5, 10-11, 13-14.
        // Vertical aisles at cols 4,7,10,13,16,19,22.
        // Horizontal aisles at rows 3, 6, 7, 8(main), 9, 12, 15, 16.
        int[] unitStarts = {2, 5, 8, 11, 14, 17, 20};
        int[][] rowBlocks = {{1, 2}, {4, 5}, {10, 11}, {13, 14}};
        int id = 0;
        for (int us : unitStarts)
            for (int[] rb : rowBlocks)
                for (int c = us; c <= us + 1; c++)
                    for (int r = rb[0]; r <= rb[1]; r++)
                        m.addEntity(new Rack("rack_" + id++, new Vector2D(c, r)));

        m.addEntity(new ChargingStation("charger",  new Vector2D(0, chargerRow)));
        m.addEntity(new DeliveryStation("delivery", new Vector2D(W - 1, chargerRow)));
        return m;
    }

    private boolean isBuiltinMap(String name) {
        return "baseline_small".equals(name) || "narrow_aisles".equals(name) || "many_intersections".equals(name);
    }

    private boolean isRandomMap(String name) {
        return "random_map".equals(name);
    }

    private int[] computeEntityBounds(com.openrobotics.map.Map map) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (MapEntity e : map.getEntities()) {
            int x = e.getPosition().getX();
            int y = e.getPosition().getY();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    // Canvas Constraints
    private void autoSetCanvasForMap(String mapName) {
        if ("empty".equals(mapName)) {
            // empty map — keep current canvas size
            return;
        }
        if (isRandomMap(mapName)) {
            // random map — set canvas to default size and allow free editing
            enforceSpinnerMin(16, 10);
            return;
        }
        com.openrobotics.map.Map m = buildBuiltinMap(mapName);
        if (m == null || m.getEntities().isEmpty()) return;
        int[] bounds = computeEntityBounds(m);
        int contentW = bounds[2] - bounds[0] + 1;
        int contentH = bounds[3] - bounds[1] + 1;
        int defaultW = contentW + 2;
        int defaultH = contentH + 2;
        SpinnerValueFactory.IntegerSpinnerValueFactory wf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) canvasWidthSpinner.getValueFactory();
        SpinnerValueFactory.IntegerSpinnerValueFactory hf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) canvasHeightSpinner.getValueFactory();
        wf.setMin(defaultW);
        hf.setMin(defaultH);
        wf.setValue(defaultW);
        hf.setValue(defaultH);
        AppState.setCanvasDimensions(defaultW, defaultH);
    }

    private void checkCanvasConstraint() {
        // Config-backed maps use fixed dimensions from the loaded engine.
        if (AppState.hasEngine() && AppState.getEngine().getMap() != null) {
            com.openrobotics.map.Map map = AppState.getEngine().getMap();
            enforceSpinnerMin(1, 1);
            canvasWarnLabel.setVisible(false);
            AppState.setCanvasDimensions(map.getWidth(), map.getHeight());
            return;
        }

        String mapName = mapCombo.getValue();
        if (mapName == null || !isBuiltinMap(mapName)) {
            enforceSpinnerMin(1, 1);
            canvasWarnLabel.setVisible(false);
            return;
        }
        com.openrobotics.map.Map previewMap = buildBuiltinMap(mapName);
        if (previewMap == null || previewMap.getEntities().isEmpty()) {
            canvasWarnLabel.setVisible(false);
            return;
        }
        int[] bounds = computeEntityBounds(previewMap);
        int contentW = bounds[2] - bounds[0] + 1;
        int contentH = bounds[3] - bounds[1] + 1;
        int minW = contentW + 2, minH = contentH + 2;
        enforceSpinnerMin(minW, minH);
        int curW = canvasWidthSpinner.getValue() != null ? canvasWidthSpinner.getValue() : minW;
        int curH = canvasHeightSpinner.getValue() != null ? canvasHeightSpinner.getValue() : minH;
        if (curW == minW || curH == minH) {
            canvasWarnLabel.setText("Min: " + minW + " width × " + minH + " height (Can't shrink further).");
            canvasWarnLabel.setVisible(true);
        } else {
            canvasWarnLabel.setVisible(false);
        }
    }

    private void enforceSpinnerMin(int minW, int minH) {
        SpinnerValueFactory.IntegerSpinnerValueFactory wf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) canvasWidthSpinner.getValueFactory();
        SpinnerValueFactory.IntegerSpinnerValueFactory hf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) canvasHeightSpinner.getValueFactory();
        wf.setMin(minW);
        hf.setMin(minH);
        if (wf.getValue() < minW) wf.setValue(minW);
        if (hf.getValue() < minH) hf.setValue(minH);
    }

    @FXML
    private void onPreviewBlocked(MouseEvent event) {
        statusLabel.setText("Map preview not interactive during setup.");
        event.consume();
    }

    private void debugStatus(String message) {
        statusLabel.setText(message);
        System.out.println("[SetupController] " + message);
    }

    // Config Loading
    private File getFallbackTestConfig() {
        File fromWorkingDir = new File(System.getProperty("user.dir"), "test_scenario.json");
        if (fromWorkingDir.isFile()) return fromWorkingDir;

        File fromParentDir = new File(System.getProperty("user.dir"), ".." + File.separator + "test_scenario.json");
        if (fromParentDir.isFile()) return fromParentDir;

        return null;
    }

    private boolean promptAndLoadConfig() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Simulation Configuration");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON Config (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File fallback = getFallbackTestConfig();
        debugStatus("Opening config chooser...");
        if (AppState.hasConfigPath()) {
            File current = new File(AppState.getConfigPath());
            File parent = current.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setInitialDirectory(parent);
            }
        } else if (fallback != null) {
            File parent = fallback.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setInitialDirectory(parent);
            }
            chooser.setInitialFileName(fallback.getName());
        }

        Window owner = ScreenNavigator.getPrimaryStage() != null
                ? ScreenNavigator.getPrimaryStage()
                : statusLabel.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            if (fallback != null) {
                debugStatus("Chooser returned no file; using fallback test_scenario.json.");
                return loadConfigFile(fallback);
            }
            debugStatus("Load cancelled (chooser returned no file).");
            return false;
        }
        return loadConfigFile(file);
    }

    private boolean loadConfigFile(File file) {
        try {
            if (file == null || !file.isFile()) {
                debugStatus("\u26a0 Selected file is not accessible: " + (file == null ? "null" : file.getAbsolutePath()));
                return false;
            }
            debugStatus("Loading config: " + file.getAbsolutePath());
            SimulationEngine engine = new SimulationEngine(file.getAbsolutePath());
            if (engine.getMap() == null) {
                String error = engine.getInitError();
                debugStatus("\u26a0 Parse failed: " + (error != null ? error : "unknown error"));
                return false;
            }
            AppState.clear();
            AppState.setConfigPath(file.getAbsolutePath());
            AppState.setEngine(engine);
            debugStatus("\u2714 Loaded: " + file.getName()
                    + "  (" + engine.getMap().getEntities().size() + " entities)");
            return true;
        } catch (Exception e) {
            debugStatus("\u26a0 Error loading file: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
            return false;
        }
    }

    private boolean goToSimulationScreen() {
        try {
            debugStatus("Opening editor screen...");
            ScreenNavigator.goToSimulation();
            return true;
        } catch (Exception e) {
            debugStatus("\u26a0 Could not open editor: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
            e.printStackTrace();
            return false;
        }
    }

    @FXML
    private void onLoadConfig() {
        if (promptAndLoadConfig()) {
            mapCombo.getSelectionModel().clearSelection();
            setConfigParamsDisabled(true);
            refreshPreview();
            checkCanvasConstraint();
        }
    }

    @FXML
    private void onRefreshFileList() {
        if (configListView == null) return;

        configListView.getItems().clear();
        File configDir = new File(CONFIG_DIRECTORY_PATH);

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File[] files = configDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".json") && !name.startsWith("."); // Hides secret config files
        });

        if (files != null && files.length > 0) {
            for (File file : files) {
                configListView.getItems().add(file.getName());
            }
        } else {
            configListView.getItems().add("No configs found.");
        }
    }

    // Start Simulation
    @FXML
    private void onStartSimulation() {
        debugStatus("Start Simulation clicked.");
        if (!validate()) return;

        if (!AppState.hasEngine()) {
            if (AppState.hasConfigPath()) {
                debugStatus("Rebuilding engine from saved config path.");
                try {
                    SimulationEngine engine = new SimulationEngine(AppState.getConfigPath());
                    if (engine.getMap() == null) {
                        String error = engine.getInitError();
                        debugStatus("\u26a0 Config reload failed: " + (error != null ? error : "unknown error"));
                        return;
                    }
                    AppState.setEngine(engine);
                } catch (Exception e) {
                    debugStatus("\u26a0 Config reload failed: " + e.getMessage());
                    return;
                }
            } else {
                SimulationEngine engine = buildEngineFromSetup();
                if (engine == null) return;
                AppState.setEngine(engine);
                persistEngineToTempFile(engine);
            }
        } else if (!AppState.hasConfigPath()) {
            // Rebuild UI-started runs so the latest field edits are applied.
            SimulationEngine engine = buildEngineFromSetup();
            if (engine == null) return;
            AppState.setEngine(engine);
            persistEngineToTempFile(engine);
        }
        goToSimulationScreen();
    }

    // Engine Construction
    // Persists a temp config so restart can rebuild runs started from setup values.
    private void persistEngineToTempFile(SimulationEngine engine) {
        try {
            File tmp = File.createTempFile("openrobotics_run_", ".json");
            tmp.deleteOnExit();
            engine.configSaving(tmp.getAbsolutePath());
            AppState.setConfigPath(tmp.getAbsolutePath());
            debugStatus("\u2714 Config persisted for restart: " + tmp.getName());
        } catch (Exception ex) {
            debugStatus("\u26a0 Could not persist temp config for restart: " + ex.getMessage());
            AppState.setConfigPath(null);
        }
    }

    // Builds a SimulationEngine from the current setup-screen values.
    private SimulationEngine buildEngineFromSetup() {
        int canvasW = canvasWidthSpinner.getValue() != null ? canvasWidthSpinner.getValue() : 16;
        int canvasH = canvasHeightSpinner.getValue() != null ? canvasHeightSpinner.getValue() : 10;

        long seed = DEFAULT_WORKLOAD_SEED;
        try {
            seed = Long.parseLong(workloadSeedField.getText().trim());
        } catch (NumberFormatException ignored) { }

        com.openrobotics.map.Map map;
        if ("random_map".equals(mapCombo.getValue())) {
            long mapSeed = seed;
            try {
                String mapSeedStr = randomSeedField.getText().trim();
                if (!mapSeedStr.isEmpty()) {
                    mapSeed = Long.parseLong(mapSeedStr);
                } else {
                    mapSeed = new java.util.Random().nextLong();
                }
            } catch (NumberFormatException ignored) { }
            map = buildRandomMap(canvasW, canvasH, mapSeed);
        } else {
            String mapName = mapCombo.getValue();
            if (mapName == null || mapName.isBlank()) {
                // Guard against a cleared map selection before engine construction.
                statusLabel.setText("\u26a0 Please select a map.");
                return null;
            }
            map = buildBuiltinMapInCanvas(mapName, canvasW, canvasH);
        }

        int maxTasks = DEFAULT_MAX_TASKS;
        try { maxTasks = Integer.parseInt(maxTasksField.getText().trim()); } catch (NumberFormatException ignored) { }

        boolean isManualMode = manualTaskRadio != null && manualTaskRadio.isSelected();

        CoordinationPolicy policy = buildCoordinationPolicy();

        Dispatcher dispatcher = new Dispatcher();
        // Seed the dispatcher with the initial rack-to-station workload.
        if (maxTasks > 0 && map != null) {
            generateFixedTasks(map, seed, maxTasks, dispatcher);
        }

        String runName = runNameField.getText().trim();
        if (runName.isEmpty()) runName = DEFAULT_RUN_NAME;

        int maxTicks = DEFAULT_MAX_TICKS;
        try { maxTicks = Integer.parseInt(maxTicksField.getText().trim()); } catch (NumberFormatException ignored) { }

        SimulationEngine engine = new SimulationEngine(map, new Robot[0], dispatcher, policy, runName, DEFAULT_TICK_MS, maxTicks, seed, maxTasks);
        engine.setManualTaskAssignment(isManualMode);

        com.openrobotics.robot.RobotConfig robotConfig = new com.openrobotics.robot.RobotConfig(
                parseOptionalFloatField(batteryCapacityField, DEFAULT_BATTERY_CAPACITY),
                parseOptionalFloatField(lowBatteryField,      DEFAULT_LOW_BATTERY),
                parseOptionalFloatField(chargePerTickField,   DEFAULT_CHARGE_PER_TICK),
                parseOptionalFloatField(energyPerMoveField,   DEFAULT_ENERGY_PER_MOVE),
                DEFAULT_LOADING_TICKS,
                DEFAULT_UNLOADING_TICKS
        );
        engine.setRobotConfig(robotConfig);

        return engine;
    }

    // Map and Task Generation
    // Copies a builtin map into the current canvas with a one-tile margin.
    private com.openrobotics.map.Map buildBuiltinMapInCanvas(String mapName, int canvasW, int canvasH) {
        com.openrobotics.map.Map src = buildBuiltinMap(mapName);
        com.openrobotics.map.Map map = new com.openrobotics.map.Map(canvasW, canvasH);
        if (src == null) return map;   // empty map — blank canvas

        int[] bounds = computeEntityBounds(src);
        int offsetX = 1 - bounds[0];
        int offsetY = 1 - bounds[1];
        for (MapEntity e : src.getEntities()) {
            int nx = e.getPosition().getX() + offsetX;
            int ny = e.getPosition().getY() + offsetY;
            Vector2D pos = new Vector2D(nx, ny);
            if (e instanceof ChargingStation) {
                map.addEntity(new ChargingStation(e.getId(), e.getName(), pos));
            } else if (e instanceof DeliveryStation) {
                map.addEntity(new DeliveryStation(e.getId(), e.getName(), pos));
            } else if (e instanceof Rack) {
                map.addEntity(new Rack(e.getId(), e.getName(), pos));
            } else if (e instanceof Obstacle) {
                map.addEntity(new Obstacle(e.getId(), e.getName(), pos));
            } else {
                map.addEntity(new MapEntity(e.getId(), e.getName(), pos));
            }
        }
        return map;
    }

    // Generates a random warehouse layout for the current canvas size.
    private com.openrobotics.map.Map buildRandomMap(int width, int height, long seed) {
        com.openrobotics.map.Map map = new com.openrobotics.map.Map(width, height);
        java.util.Random rng = new java.util.Random(seed);

        int chargerY = rng.nextInt(height - 2) + 1;
        map.addEntity(new ChargingStation("charger", new Vector2D(0, chargerY)));

        int deliveryY = rng.nextInt(height - 2) + 1;
        map.addEntity(new DeliveryStation("delivery", new Vector2D(width - 1, deliveryY)));

        Set<String> occupied = new HashSet<>();
        occupied.add("0," + chargerY);
        occupied.add((width - 1) + "," + deliveryY);

        int interiorCells = (width - 2) * (height - 2);
        int rackCount = Math.max(2, (int) Math.round(interiorCells * 0.15));

        int placed = 0, attempts = 0;
        while (placed < rackCount && attempts < rackCount * 10) {
            attempts++;
            int rx = rng.nextInt(width - 2) + 1;
            int ry = rng.nextInt(height - 2) + 1;
            String key = rx + "," + ry;
            if (!occupied.contains(key)) {
                if (rx == 0 || rx == width - 1) continue;
                map.addEntity(new Rack("rack_" + placed, new Vector2D(rx, ry)));
                occupied.add(key);
                placed++;
            }
        }

        int obstacleCount = Math.max(1, width * height / 100);
        int obsPlaced = 0;
        attempts = 0;
        while (obsPlaced < obstacleCount && attempts < obstacleCount * 10) {
            attempts++;
            int ox = rng.nextInt(width);
            int oy = rng.nextInt(height);
            String key = ox + "," + oy;
            if (!occupied.contains(key)) {
                map.addEntity(new Obstacle("obstacle_" + obsPlaced, new Vector2D(ox, oy)));
                occupied.add(key);
                obsPlaced++;
            }
        }

        return map;
    }

    private Vector2D findSpawnTile(Vector2D center, com.openrobotics.map.Map map, Set<String> occupied) {
        int cx = (int) center.getX();
        int cy = (int) center.getY();
        for (int d = 0; d < Math.max(map.getWidth(), map.getHeight()); d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dy = -d; dy <= d; dy++) {
                    if (Math.abs(dx) != d && Math.abs(dy) != d) continue;
                    int tx = cx + dx, ty = cy + dy;
                    String key = tx + "," + ty;
                    if (tx >= 0 && tx < map.getWidth() && ty >= 0 && ty < map.getHeight()
                            && !occupied.contains(key)) {
                        Tile tile = map.getTile(tx, ty);
                        if (tile != null && map.isTraversable(new Vector2D(tx, ty))) {
                            return new Vector2D(tx, ty);
                        }
                    }
                }
            }
        }
        return center; // fallback
    }

    // Seeds the dispatcher with the initial automatically generated tasks.
    private void generateFixedTasks(com.openrobotics.map.Map map, long seed, int count, Dispatcher dispatcher) {
        List<Task> tasks = TaskGenerator.generateAutomaticTasks(map, count, seed);
        for (Task task : tasks) {
            dispatcher.addTask(task);
        }
    }

    private CoordinationPolicy buildCoordinationPolicy() {
        String policyName = policyCombo.getValue();
        if (policyName == null) policyName = DEFAULT_POLICY;

        return switch (policyName) {
            case "RESERVATION_K" -> {
                int k = reservationKSpinner.getValue() != null ? reservationKSpinner.getValue() : DEFAULT_RESERVATION_K;
                yield new ReservationKPolicy(k);
            }
            case "TRAFFIC_RULES" -> new TrafficRulesPolicy(new HashSet<>());
            default -> CoordinationPolicy.noOp();
        };
    }

    // Parsing Helpers
    private int parseIntSafe(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private void setTextIfPresent(TextField field, String value) {
        if (field != null) field.setText(value);
    }

    private float parseOptionalFloatField(TextField field, float fallback) {
        return field == null ? fallback : parseFloatSafe(field.getText(), fallback);
    }

    private float parseFloatSafe(String text, float fallback) {
        try { return Float.parseFloat(text.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    // Validation

    // Validates the minimum required inputs before starting the simulation.
    private boolean validate() {
        // Map Check
        if (!AppState.hasEngine() && mapCombo.getValue() == null) {
            statusLabel.setText("⚠ Please select a map or load a config file.");
            return false;
        }

        if ("RESERVATION_K".equals(policyCombo.getValue())) {
            if (!checkSpinnerBound(reservationKSpinner, "Reservation K", 1, 50)) return false;
        }

        // Integer Bounds
        if (!checkIntBound(maxTasksField, "Max Tasks", 1, 500)) return false;
        if (!checkIntBound(maxTicksField, "Max Ticks", 1, 1000000)) return false;

        // Float Bounds
        if (batteryCapacityField != null && batteryCapacityField.getScene() != null) {
            if (!checkFloatBound(batteryCapacityField, "Battery Capacity", 1.0f, 1000.0f)) return false;
            if (!checkFloatBound(lowBatteryField, "Low Battery", 0.0f, 1000.0f)) return false;
            if (!checkFloatBound(chargePerTickField, "Charge Per Tick", 0.0f, 100.0f)) return false;
            if (!checkFloatBound(energyPerMoveField, "Energy Per Move", 0.0f, 100.0f)) return false;

            float cap = Float.parseFloat(batteryCapacityField.getText().trim());
            float low = Float.parseFloat(lowBatteryField.getText().trim());
            if (low >= cap) {
                statusLabel.setText("⚠ Low battery threshold must be lower than total capacity.");
                return false;
            }
        }

        statusLabel.setText(""); // Clear errors
        return true;
    }

    private boolean checkIntBound(TextField field, String fieldName, int min, int max) {
        if (field == null) return true;
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < min || value > max) {
                statusLabel.setText("⚠ " + fieldName + " must be between " + min + " and " + max + ".");
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠ " + fieldName + " requires a valid number.");
            return false;
        }
    }

    private boolean checkFloatBound(TextField field, String fieldName, float min, float max) {
        if (field == null) return true;
        try {
            float value = Float.parseFloat(field.getText().trim());
            if (value < min || value > max) {
                statusLabel.setText("⚠ " + fieldName + " must be between " + min + " and " + max + ".");
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠ " + fieldName + " requires a valid number.");
            return false;
        }
    }

    private boolean checkSpinnerBound(Spinner<Integer> spinner, String fieldName, int min, int max) {
        if (spinner == null || spinner.isDisabled()) return true;
        try {
            // Check the editor text because Spinner.getValue() might not have changed yet
            String text = spinner.getEditor().getText().trim();
            if (text.isEmpty()) {
                statusLabel.setText("⚠ " + fieldName + " cannot be empty.");
                return false;
            }

            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                statusLabel.setText("⚠ " + fieldName + " must be between " + min + " and " + max + ".");
                return false;
            }

            // Force the spinner to adopt the typed value internally
            spinner.getValueFactory().setValue(value);
            return true;
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠ " + fieldName + " requires a valid number.");
            return false;
        }
    }

    /** Restricts a TextField to only accept positive integers */
    private void makeIntegerOnly(TextField field) {
        if (field == null) return;
        field.setTextFormatter(new TextFormatter<>(change -> {
            // Allows empty string (while deleting) or digits only
            if (change.getControlNewText().matches("\\d*")) {
                return change;
            }
            return null;
        }));
    }

    /** Restricts a TextField to accept positive floats (decimals) */
    private void makeFloatOnly(TextField field) {
        if (field == null) return;
        field.setTextFormatter(new TextFormatter<>(change -> {
            // Allows empty, digits, or digits with a single decimal point
            if (change.getControlNewText().matches("\\d*(\\.\\d*)?")) {
                return change;
            }
            return null;
        }));
    }

    /** Restricts an editable Spinner to only accept positive integers */
    private void makeSpinnerIntegerOnly(Spinner<Integer> spinner) {
        if (spinner == null || !spinner.isEditable()) return;
        TextField editor = spinner.getEditor();
        editor.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("\\d*")) {
                return change;
            }
            return null; // Reject the keystroke
        }));
    }

    /**
     * Disables or re-enables all user-editable parameters on the setup screen.
     * Called with {@code true} whenever a config file is loaded and with {@code false} when switching back to a template map.
     * The reservationK spinner has its own separate policy-driven disable logic
     */
    private void setConfigParamsDisabled(boolean disabled) {

        // Coordination policy
        policyCombo.setDisable(disabled);
        if (!disabled) {
            // Re-evaluate the policy-driven disable for reservationK
            reservationKSpinner.setDisable(!"RESERVATION_K".equals(policyCombo.getValue()));
        } else {
            reservationKSpinner.setDisable(true);
        }

        // Task settings
        if (autoTaskRadio  != null) autoTaskRadio.setDisable(disabled);
        if (manualTaskRadio != null) manualTaskRadio.setDisable(disabled);
        maxTasksField.setDisable(disabled);
        workloadSeedField.setDisable(disabled);

        // Robot physics
        setDisableIfPresent(batteryCapacityField, disabled);
        setDisableIfPresent(lowBatteryField, disabled);
        setDisableIfPresent(chargePerTickField, disabled);
        setDisableIfPresent(energyPerMoveField, disabled);

        // Run settings
        maxTicksField.setDisable(disabled);
        runNameField.setDisable(disabled);

        // Canvas size
        canvasWidthSpinner.setDisable(disabled);
        canvasHeightSpinner.setDisable(disabled);
    }

    private void setDisableIfPresent(TextField field, boolean disabled) {
        if (field != null) field.setDisable(disabled);
    }

    // Navigation

    @FXML
    private void onMenuWelcome() { ScreenNavigator.goToWelcome(); }

    @FXML
    private void onMenuGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/dan-moraru/OpenRobotics"));
        } catch (Exception ex) {
            statusLabel.setText("Could not open browser.");
        }
    }

    @FXML
    private void onExit() {
        if (ScreenNavigator.confirmExit()) {
            javafx.application.Platform.exit();
        }
    }
}