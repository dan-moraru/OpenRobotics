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
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.navigation.BugNavigationStrategy;
import com.openrobotics.robot.navigation.RtaStarNavigationStrategy;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import com.openrobotics.task.Task;
import com.openrobotics.util.IconLoader;
import com.openrobotics.util.ScreenNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for {@code SetupScreen.fxml}.
 *
 * <p>Manages all simulation configuration inputs (§4.1.2):
 * <ul>
 *   <li>Map selection / random map generation</li>
 *   <li>Robot count + navigation algorithm</li>
 *   <li>Coordination policy + reservation k</li>
 *   <li>Workload parameters</li>
 *   <li>Simulation tick settings</li>
 *   <li>Load / Save configuration file</li>
 *   <li>Start Simulation</li>
 * </ul>
 */
public class SetupController {

    // ── MAP ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> mapCombo;
    @FXML private CheckBox         randomMapCheck;
    @FXML private TextField        randomSeedField;

    // ── COORDINATION POLICY ─────────────────────────────────────────────
    @FXML private ComboBox<String> policyCombo;
    @FXML private Spinner<Integer> reservationKSpinner;

    // ── WORKLOAD ────────────────────────────────────────────────────────
    @FXML private ComboBox<String> workloadModeCombo;
    @FXML private TextField        spawnRateField;
    @FXML private TextField        maxTasksField;
    @FXML private TextField        workloadSeedField;

    // ── SIMULATION ──────────────────────────────────────────────────────
    @FXML private TextField maxTicksField;

    // ── RUN META ────────────────────────────────────────────────────────
    @FXML private TextField runNameField;

    // ── CANVAS ──────────────────────────────────────────────────────────
    @FXML private Spinner<Integer> canvasWidthSpinner;
    @FXML private Spinner<Integer> canvasHeightSpinner;
    @FXML private Canvas    previewCanvas;
    @FXML private StackPane viewportPreviewStack;
    @FXML private Label     canvasWarnLabel;

    // ── STATUS ──────────────────────────────────────────────────────────
    @FXML private Label statusLabel;

    // ------------------------------------------------------------------ //
    //  Default values (spec §5.1.3.4)
    // ------------------------------------------------------------------ //

    private static final String DEFAULT_POLICY        = "NONE";
    private static final int    DEFAULT_RESERVATION_K = 3;
    private static final String DEFAULT_WORKLOAD_MODE = "SPAWN_RATE";
    private static final int    DEFAULT_SPAWN_RATE    = 1;
    private static final int    DEFAULT_MAX_TASKS     = 10;
    private static final long   DEFAULT_WORKLOAD_SEED = 42;
    private static final int    DEFAULT_MAX_TICKS     = 30000;
    private static final String DEFAULT_RUN_NAME      = "experiment_1";
    private static final int    DEFAULT_CANVAS_TILES  = 15;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Map dropdown
        mapCombo.setItems(FXCollections.observableArrayList(
                "baseline_small", "narrow_aisles", "many_intersections"));
        mapCombo.getSelectionModel().selectFirst();

        reservationKSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, DEFAULT_RESERVATION_K));

        // Coordination policy
        policyCombo.setItems(FXCollections.observableArrayList(
                "NONE", "TRAFFIC_RULES", "RESERVATION_K"));
        policyCombo.getSelectionModel().select(DEFAULT_POLICY);

        // Workload mode
        workloadModeCombo.setItems(FXCollections.observableArrayList(
                "SPAWN_RATE", "FIXED_LIST"));
        workloadModeCombo.getSelectionModel().select(DEFAULT_WORKLOAD_MODE);

        // Canvas size spinners
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

        // Default text fields
        spawnRateField.setText(String.valueOf(DEFAULT_SPAWN_RATE));
        maxTasksField.setText(String.valueOf(DEFAULT_MAX_TASKS));
        workloadSeedField.setText(String.valueOf(DEFAULT_WORKLOAD_SEED));
        maxTicksField.setText(String.valueOf(DEFAULT_MAX_TICKS));
        runNameField.setText(DEFAULT_RUN_NAME);

        // Disable reservation k spinner unless policy is RESERVATION_K
        reservationKSpinner.setDisable(true);
        policyCombo.valueProperty().addListener((obs, oldVal, newVal) ->
                reservationKSpinner.setDisable(!"RESERVATION_K".equals(newVal)));

        // Resize canvas to fill its parent without creating a layout min-width constraint
        viewportPreviewStack.widthProperty().addListener((obs, o, n) -> {
            previewCanvas.setWidth(n.doubleValue());
            refreshPreview();
        });
        viewportPreviewStack.heightProperty().addListener((obs, o, n) -> {
            previewCanvas.setHeight(n.doubleValue());
            refreshPreview();
        });

        mapCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) autoSetCanvasForMap(n);
            refreshPreview();
            checkCanvasConstraint();
        });

        // Apply canvas sizing for the initial map selection (listener fires only on changes)
        String initialMap = mapCombo.getValue();
        if (initialMap != null) autoSetCanvasForMap(initialMap);
    }

    // ------------------------------------------------------------------ //
    //  Reset Handlers
    // ------------------------------------------------------------------ //

    @FXML private void onResetMap() {
        mapCombo.getSelectionModel().selectFirst();
        refreshPreview();
        checkCanvasConstraint();
    }
    @FXML private void onResetRandomMap()    { randomMapCheck.setSelected(false); refreshPreview(); }
    @FXML private void onResetRandomSeed()   { randomSeedField.setText(""); }
    @FXML private void onResetPolicy()       { policyCombo.getSelectionModel().select(DEFAULT_POLICY); }
    @FXML private void onResetWorkloadMode() { workloadModeCombo.getSelectionModel().select(DEFAULT_WORKLOAD_MODE); }
    @FXML private void onResetSpawnRate()    { spawnRateField.setText(String.valueOf(DEFAULT_SPAWN_RATE)); }
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

    // ------------------------------------------------------------------ //
    //  Preview Canvas
    // ------------------------------------------------------------------ //

    private void refreshPreview() {
        double vw = previewCanvas.getWidth();
        double vh = previewCanvas.getHeight();
        if (vw <= 0 || vh <= 0) return;

        GraphicsContext gc = previewCanvas.getGraphicsContext2D();
        int cw = canvasWidthSpinner.getValue() != null ? canvasWidthSpinner.getValue() : AppState.getCanvasWidthTiles();
        int ch = canvasHeightSpinner.getValue() != null ? canvasHeightSpinner.getValue() : AppState.getCanvasHeightTiles();

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

        String mapName = mapCombo.getValue();
        if (mapName != null) {
            com.openrobotics.map.Map previewMap = buildBuiltinMap(mapName);
            if (previewMap != null && !previewMap.getEntities().isEmpty()) {
                int[] bounds = computeEntityBounds(previewMap);
                int minX = bounds[0], minY = bounds[1], maxX = bounds[2], maxY = bounds[3];
                int contentW = maxX - minX + 1, contentH = maxY - minY + 1;
                int tileOffX = (cw - contentW) / 2 - minX;
                int tileOffY = (ch - contentH) / 2 - minY;
                int robotCount = 4; // TODO: replace with per-robot editor config
                Set<String> robotTiles = computeRobotPositions(previewMap, robotCount);
                drawPreviewEntities(gc, previewMap, bx, by, tileSize, tileOffX, tileOffY, robotTiles);
                drawPreviewRobots(gc, previewMap, bx, by, tileSize, tileOffX, tileOffY, robotTiles);
            }
        }
    }

    private void drawPreviewEntities(GraphicsContext gc, com.openrobotics.map.Map map,
                                     double ox, double oy, double tileSize,
                                     int tileOffX, int tileOffY, Set<String> robotTiles) {
        double pad = Math.max(1.0, tileSize * 0.06);
        for (MapEntity entity : map.getEntities()) {
            String key = entity.getPosition().getX() + "," + entity.getPosition().getY();

            // FIX #4: Always draw stations (charger/delivery) even when a robot occupies
            // the same tile — the robot icon will be drawn on top in drawPreviewRobots().
            boolean isStation = (entity instanceof ChargingStation) || (entity instanceof DeliveryStation);
            if (!isStation && robotTiles.contains(key)) continue;

            double sx = ox + (tileOffX + entity.getPosition().getX()) * tileSize;
            double sy = oy + (tileOffY + entity.getPosition().getY()) * tileSize;
            Image icon = resolvePreviewIcon(entity);
            boolean wallLike = entity instanceof Obstacle;
            if (icon != null && !icon.isError()) {
                if (wallLike) gc.drawImage(icon, sx, sy, tileSize, tileSize);
                else          gc.drawImage(icon, sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad);
            } else {
                if (entity instanceof Rack)              { gc.setFill(Color.web("#8D8A7F")); gc.fillRect(sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad); }
                else if (entity instanceof ChargingStation) { gc.setFill(Color.web("#599068")); gc.fillRect(sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad); }
                else if (entity instanceof DeliveryStation) { gc.setFill(Color.web("#599068")); gc.fillRect(sx+pad, sy+pad, tileSize-2*pad, tileSize-2*pad); }
                else if (entity instanceof Obstacle)     { gc.setFill(Color.web("#5D5B54")); gc.fillRect(sx, sy, tileSize, tileSize); }
            }
        }
    }

    private Image resolvePreviewIcon(MapEntity entity) {
        List<String> candidates = new ArrayList<>();
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

    /** Pre-compute which tiles robots occupy (charger tile first, then adjacent empty tiles). */
    private Set<String> computeRobotPositions(com.openrobotics.map.Map map, int count) {
        int spawnX = 0, spawnY = map.getHeight() / 2;
        for (MapEntity e : map.getEntities()) {
            if (e instanceof ChargingStation) {
                spawnX = e.getPosition().getX();
                spawnY = e.getPosition().getY();
                break;
            }
        }
        // Only non-charger entities block robot placement
        Set<String> blocked = new HashSet<>();
        for (MapEntity e : map.getEntities()) {
            if (!(e instanceof ChargingStation)) {
                blocked.add(e.getPosition().getX() + "," + e.getPosition().getY());
            }
        }
        Set<String> positions = new LinkedHashSet<>();
        for (int delta = 0; positions.size() < count && delta < map.getHeight(); delta++) {
            int[] ys = (delta == 0) ? new int[]{spawnY} : new int[]{spawnY + delta, spawnY - delta};
            for (int cy : ys) {
                if (positions.size() >= count) break;
                if (cy < 0 || cy >= map.getHeight()) continue;
                if (blocked.contains(spawnX + "," + cy)) continue;
                positions.add(spawnX + "," + cy);
            }
        }
        return positions;
    }

    /**
     * Draw robot icons at the pre-computed tile positions (all identical robot icon).
     */
    private void drawPreviewRobots(GraphicsContext gc, com.openrobotics.map.Map map,
                                   double ox, double oy, double tileSize,
                                   int tileOffX, int tileOffY, Set<String> robotTiles) {
        Image robotIcon = IconLoader.getIcon("ROBOT");
        double pad = Math.max(1.0, tileSize * 0.06);

        // extra padding specifically for the robot icon so it shrinks
        double robotPad = pad + (tileSize * 0.10);

        for (String pos : robotTiles) {
            String[] parts = pos.split(",");
            int tx = Integer.parseInt(parts[0]);
            int ty = Integer.parseInt(parts[1]);
            double sx = ox + (tileOffX + tx) * tileSize;
            double sy = oy + (tileOffY + ty) * tileSize;

            if (robotIcon != null && !robotIcon.isError()) {
                // Draw the image smaller using robotPad
                gc.drawImage(robotIcon, sx + robotPad, sy + robotPad, tileSize - 2*robotPad, tileSize - 2*robotPad);
            } else {
                gc.setFill(Color.web("#C2BEAE"));
                gc.fillOval(sx + robotPad, sy + robotPad, tileSize - 2*robotPad, tileSize - 2*robotPad);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Built-in Map Builders
    // ------------------------------------------------------------------ //

    private com.openrobotics.map.Map buildBuiltinMap(String name) {
        return switch (name) {
            case "baseline_small"     -> buildBaselineSmall();
            case "narrow_aisles"      -> buildNarrowAisles();
            case "many_intersections" -> buildManyIntersections();
            default                   -> null;
        };
    }

    /**
     * FIX #2 – baseline_small (16 × 10).
     *
     * Layout:
     * <pre>
     *  y\x  0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
     *   0   W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W
     *   1   W  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *   2   W  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *   3   W  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *   4   C  .  .  .  .  .  .  .  .  .  .  .  .  .  .  D  ← main aisle
     *   5   W  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *   6   W  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *   7   W  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *   8   W  .  .  .  .  .  .  .  .  .  .  .  .  .  .  W  ← lower aisle
     *   9   W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W
     * </pre>
     * Vertical aisles at cols 4, 7, 10.  Charger/delivery on side-wall openings.
     */
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

    /**
     * FIX #2 – narrow_aisles (22 × 14).
     *
     * Six 2-wide shelving units with tall rack blocks and only 1-tile vertical aisles.
     * <pre>
     *  cols: 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21
     *  y=0 : W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W
     *  y=1 : W  .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *  ...5: W  .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *  y=6 : C  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  D  ← main aisle
     *  y=7 : W  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  W  ← lower aisle
     *  y=8 : W  .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *  ...12: W .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  R  R  .  .  W
     *  y=13: W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W  W
     * </pre>
     */
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

    /**
     * FIX #2 – many_intersections (26 × 18).
     *
     * Seven 2-wide shelving units × four short rack blocks, creating a dense
     * grid of crossing aisles (14 intersection points per horizontal aisle).
     * <pre>
     *  cols: 0 1 2 3 | 5 6 | 8 9 |11 12|14 15|17 18|20 21| 23 24 25
     *  y=0 : W  W  W  W  W  ...wall...  W
     *  y=1 : W  . [R  R] . [R  R] ...
     *  y=2 : W  . [R  R] . [R  R] ...
     *  y=3 : W  .  .  .  .  .  ... (aisle)
     *  y=4 : W  . [R  R] . [R  R] ...
     *  y=5 : W  . [R  R] . [R  R] ...
     *  y=6 : W  .  .  .  .  .  ... (aisle)
     *  y=7 : W  .  .  .  .  .  ... (wider aisle)
     *  y=8 : C  .  .  .  .  .  ...  D  ← main aisle
     *  y=9 : W  .  .  .  .  .  ... (wider aisle)
     *  y=10: W  . [R  R] . [R  R] ...
     *  y=11: W  . [R  R] . [R  R] ...
     *  y=12: W  .  .  .  .  .  ... (aisle)
     *  y=13: W  . [R  R] . [R  R] ...
     *  y=14: W  . [R  R] . [R  R] ...
     *  y=15: W  .  .  .  .  .  ... (aisle)
     *  y=16: W  .  .  .  .  .  ...
     *  y=17: W  W  W  W  W  ...wall...  W
     * </pre>
     */
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

    // ------------------------------------------------------------------ //
    //  Canvas Constraint + Auto-sizing
    // ------------------------------------------------------------------ //

    private boolean isBuiltinMap(String name) {
        return "baseline_small".equals(name) || "narrow_aisles".equals(name) || "many_intersections".equals(name);
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

    private void autoSetCanvasForMap(String mapName) {
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

    // ------------------------------------------------------------------ //
    //  Blocked preview click
    // ------------------------------------------------------------------ //

    @FXML
    private void onPreviewBlocked(MouseEvent event) {
        statusLabel.setText("Map preview not interactive during setup.");
        event.consume();
    }

    // ------------------------------------------------------------------ //
    //  Config File Actions
    // ------------------------------------------------------------------ //

    private void debugStatus(String message) {
        statusLabel.setText(message);
        System.out.println("[SetupController] " + message);
    }

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
            goToSimulationScreen();
        }
    }

    @FXML
    private void onSaveConfig() {
        if (!AppState.hasEngine()) {
            debugStatus("\u26a0 Load a configuration first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Simulation Configuration");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Config (*.json)", "*.json"));
        chooser.setInitialFileName("config_saved.json");
        File file = chooser.showSaveDialog(statusLabel.getScene().getWindow());
        if (file == null) {
            debugStatus("Save cancelled.");
            return;
        }
        try {
            AppState.getEngine().configSaving(file.getAbsolutePath());
            debugStatus("\u2714 Saved: " + file.getName());
        } catch (Exception e) {
            debugStatus("\u26a0 Save error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Start Simulation
    // ------------------------------------------------------------------ //

    @FXML
    private void onStartSimulation() {
        debugStatus("Start Simulation clicked.");
        if (!validate()) return;

        if (!AppState.hasEngine()) {
            if (AppState.hasConfigPath()) {
                // Rebuild engine from previously loaded config path
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
                // Build engine from current setup screen values
                SimulationEngine engine = buildEngineFromSetup();
                if (engine == null) return;
                AppState.setEngine(engine);
                // persist to a temp JSON so SimulationController.onRestart() can reload it
                persistEngineToTempFile(engine);
            }
        } else if (!AppState.hasConfigPath()) {
            // Engine exists but was built from UI values, rebuild so latest field values apply.
            SimulationEngine engine = buildEngineFromSetup();
            if (engine == null) return;
            AppState.setEngine(engine);
            persistEngineToTempFile(engine);
        }
        goToSimulationScreen();
    }

    /**
     * Saves the given engine to a temp JSON file and stores the path in AppState.
     * This allows SimulationController.onRestart() to reload the engine even when the user
     * started from a template map (no pre-existing config file).
     */
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

    /**
     * Constructs a SimulationEngine entirely from the current UI field values.
     */
    private SimulationEngine buildEngineFromSetup() {
        int canvasW = canvasWidthSpinner.getValue() != null ? canvasWidthSpinner.getValue() : 16;
        int canvasH = canvasHeightSpinner.getValue() != null ? canvasHeightSpinner.getValue() : 10;

        long seed = DEFAULT_WORKLOAD_SEED;
        try {
            seed = Long.parseLong(workloadSeedField.getText().trim());
        } catch (NumberFormatException ignored) { }

        com.openrobotics.map.Map map;
        if (randomMapCheck.isSelected()) {
            long mapSeed = seed;
            try {
                String mapSeedStr = randomSeedField.getText().trim();
                if (!mapSeedStr.isEmpty()) mapSeed = Long.parseLong(mapSeedStr);
            } catch (NumberFormatException ignored) { }
            map = buildRandomMap(canvasW, canvasH, mapSeed);
        } else {
            String mapName = mapCombo.getValue();
            if (mapName == null) {
                statusLabel.setText("\u26a0 Please select a map.");
                return null;
            }
            map = buildBuiltinMapInCanvas(mapName, canvasW, canvasH);
        }

        String workloadMode = workloadModeCombo.getValue();
        if (workloadMode == null) workloadMode = DEFAULT_WORKLOAD_MODE;

        int spawnRate = DEFAULT_SPAWN_RATE;
        try { spawnRate = Integer.parseInt(spawnRateField.getText().trim()); } catch (NumberFormatException ignored) { }

        int maxTasks = DEFAULT_MAX_TASKS;
        try { maxTasks = Integer.parseInt(maxTasksField.getText().trim()); } catch (NumberFormatException ignored) { }

        CoordinationPolicy policy = buildCoordinationPolicy();

        int robotCount = 4;
        String navAlgo = "GREEDY";

        List<Vector2D> spawnCandidates = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof ChargingStation) spawnCandidates.add(e.getPosition());
        }
        if (spawnCandidates.isEmpty()) {
            spawnCandidates.add(new Vector2D(0, canvasH / 2));
        }

        Set<String> occupiedTiles = new HashSet<>();
        for (MapEntity e : map.getEntities()) {
            occupiedTiles.add(e.getPosition().getX() + "," + e.getPosition().getY());
        }

        Robot[] robots = new Robot[robotCount];
        for (int i = 0; i < robotCount; i++) {
            Vector2D spawn = findSpawnTile(spawnCandidates.get(0), map, occupiedTiles);
            Robot robot = new Robot("robot_" + i, spawn);
            occupiedTiles.add(spawn.getX() + "," + spawn.getY());
            switch (navAlgo) {
                case "GREEDY"   -> robot.setNav(new GreedyNavigationStrategy(seed));
                case "BUG"      -> robot.setNav(new BugNavigationStrategy(seed));
                case "RTA_STAR" -> robot.setNav(new RtaStarNavigationStrategy(seed));
                default         -> robot.setNav(new GreedyNavigationStrategy(seed));
            }
            robot.setSensor(new ProximitySensor());
            map.addEntity(robot);
            robots[i] = robot;
        }

        Dispatcher dispatcher = new Dispatcher();
        if ("FIXED_LIST".equals(workloadMode)) {
            generateFixedTasks(map, seed, maxTasks, dispatcher);
        }

        String runName = runNameField.getText().trim();
        if (runName.isEmpty()) runName = DEFAULT_RUN_NAME;

        int maxTicks = DEFAULT_MAX_TICKS;
        try { maxTicks = Integer.parseInt(maxTicksField.getText().trim()); } catch (NumberFormatException ignored) { }

        return new SimulationEngine(map, robots, dispatcher, policy, runName, 50, maxTicks, seed, workloadMode, spawnRate, maxTasks);
    }

    /**
     * Constructs a builtin map scaled/centred to fit the canvas dimensions.
     */
    private com.openrobotics.map.Map buildBuiltinMapInCanvas(String mapName, int canvasW, int canvasH) {
        com.openrobotics.map.Map src = buildBuiltinMap(mapName);
        com.openrobotics.map.Map map = new com.openrobotics.map.Map(canvasW, canvasH);

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

    /**
     * Generates a random warehouse map of the given dimensions.
     */
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

    /**
     * Generates up to maxTasks pickup-to-dropoff tasks.
     * Task positions are checked for traversability first; if a rack tile is
     * non-traversable the nearest traversable neighbour is used instead so robots
     * can always reach the assigned location.
     */
    private void generateFixedTasks(com.openrobotics.map.Map map, long seed, int maxTasks, Dispatcher dispatcher) {
        List<Vector2D> pickups  = new ArrayList<>();
        List<Vector2D> dropoffs = new ArrayList<>();

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (MapEntity e : map.getEntities()) {
            if (e instanceof Rack) {
                Vector2D pos = e.getPosition();
                if (map.isTraversable(pos)) {
                    pickups.add(pos);
                } else {
                    // rack tile is blocked, use a traversable neighbour instead
                    for (int[] d : dirs) {
                        int nx = pos.getX() + d[0], ny = pos.getY() + d[1];
                        if (nx >= 0 && nx < map.getWidth() && ny >= 0 && ny < map.getHeight()) {
                            Vector2D adj = new Vector2D(nx, ny);
                            if (map.isTraversable(adj)) {
                                pickups.add(adj);
                                break;
                            }
                        }
                    }
                }
            } else if (e instanceof DeliveryStation) {
                Vector2D pos = e.getPosition();
                if (map.isTraversable(pos)) {
                    dropoffs.add(pos);
                } else {
                    for (int[] d : dirs) {
                        int nx = pos.getX() + d[0], ny = pos.getY() + d[1];
                        if (nx >= 0 && nx < map.getWidth() && ny >= 0 && ny < map.getHeight()) {
                            Vector2D adj = new Vector2D(nx, ny);
                            if (map.isTraversable(adj)) {
                                dropoffs.add(adj);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Fallback pickups: any traversable non-dropoff tile
        if (pickups.isEmpty()) {
            for (int y = 0; y < map.getHeight(); y++)
                for (int x = 0; x < map.getWidth(); x++) {
                    Vector2D p = new Vector2D(x, y);
                    if (map.isTraversable(p) && dropoffs.stream().noneMatch(d -> d.equals(p)))
                        pickups.add(p);
                }
        }
        // Fallback dropoffs: any traversable non-pickup tile
        if (dropoffs.isEmpty()) {
            outer:
            for (int y = 0; y < map.getHeight(); y++)
                for (int x = 0; x < map.getWidth(); x++) {
                    Vector2D p = new Vector2D(x, y);
                    if (map.isTraversable(p) && pickups.stream().noneMatch(pk -> pk.equals(p))) {
                        dropoffs.add(p);
                        break outer;
                    }
                }
        }
        if (pickups.isEmpty() || dropoffs.isEmpty()) return;

        java.util.Random rng = new java.util.Random(seed);
        int taskId = 1, placed = 0, attempts = 0;
        while (placed < maxTasks && attempts < maxTasks * 5) {
            attempts++;
            Vector2D pickup  = pickups.get(rng.nextInt(pickups.size()));
            Vector2D dropoff;
            do { dropoff = dropoffs.get(rng.nextInt(dropoffs.size())); }
            while (dropoff.equals(pickup) && dropoffs.size() > 1);
            dispatcher.addTask(new Task(taskId++, pickup, dropoff, 1));
            placed++;
        }
    }

    /** Basic validation – returns {@code true} if all required fields are filled. */
    private boolean validate() {
        if (mapCombo.getValue() == null && !randomMapCheck.isSelected()) {
            statusLabel.setText("\u26a0 Please select a map or enable Random Map.");
            return false;
        }
        try {
            int ticks = Integer.parseInt(maxTicksField.getText().trim());
            if (ticks <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("\u26a0 Max ticks must be a positive integer.");
            return false;
        }
        statusLabel.setText("");
        return true;
    }

    // ------------------------------------------------------------------ //
    //  Tab strip
    // ------------------------------------------------------------------ //

    @FXML
    private void onTabEditor() { /* already on setup/editor screen */ }

    // ------------------------------------------------------------------ //
    //  Menu items
    // ------------------------------------------------------------------ //

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

    // ------------------------------------------------------------------ //
    //  Global Exit
    // ------------------------------------------------------------------ //

    @FXML
    private void onExit() {
        if (ScreenNavigator.confirmExit()) {
            javafx.application.Platform.exit();
        }
    }
}