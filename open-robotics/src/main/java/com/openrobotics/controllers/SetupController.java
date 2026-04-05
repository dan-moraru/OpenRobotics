package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.map.entities.station.Station;
import com.openrobotics.simulationcore.SimulationEngine;
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

    // ── ROBOTS ──────────────────────────────────────────────────────────
    @FXML private Spinner<Integer> robotCountSpinner;
    @FXML private ComboBox<String> navAlgoCombo;

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
    @FXML private TextField tickMsField;

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

    private static final int    DEFAULT_ROBOT_COUNT   = 4;
    private static final String DEFAULT_NAV_ALGO      = "GREEDY";
    private static final String DEFAULT_POLICY        = "NONE";
    private static final int    DEFAULT_RESERVATION_K = 3;
    private static final String DEFAULT_WORKLOAD_MODE = "SPAWN_RATE";
    private static final String DEFAULT_SPAWN_RATE    = "10";
    private static final String DEFAULT_MAX_TASKS     = "200";
    private static final String DEFAULT_WORKLOAD_SEED = "42";
    private static final String DEFAULT_MAX_TICKS     = "30000";
    private static final String DEFAULT_TICK_MS       = "50";
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

        robotCountSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, DEFAULT_ROBOT_COUNT));
        reservationKSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, DEFAULT_RESERVATION_K));

        // Navigation algorithm
        navAlgoCombo.setItems(FXCollections.observableArrayList(
                "GREEDY", "BUG", "RTA_STAR"));
        navAlgoCombo.getSelectionModel().select(DEFAULT_NAV_ALGO);

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
        spawnRateField.setText(DEFAULT_SPAWN_RATE);
        maxTasksField.setText(DEFAULT_MAX_TASKS);
        workloadSeedField.setText(DEFAULT_WORKLOAD_SEED);
        maxTicksField.setText(DEFAULT_MAX_TICKS);
        tickMsField.setText(DEFAULT_TICK_MS);
        runNameField.setText(DEFAULT_RUN_NAME);

        // Disable reservation k spinner unless policy is RESERVATION_K
        reservationKSpinner.setDisable(true);
        policyCombo.valueProperty().addListener((obs, oldVal, newVal) ->
                reservationKSpinner.setDisable(!"RESERVATION_K".equals(newVal)));

        // Resize canvas to fill its parent without creating a layout min-width constraint
        // (binding would freeze the SplitPane divider when dragging right)
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
        robotCountSpinner.valueProperty().addListener((obs, o, n) -> refreshPreview());

        // Apply canvas sizing for the initial map selection (listener fires only on changes)
        String initialMap = mapCombo.getValue();
        if (initialMap != null) autoSetCanvasForMap(initialMap);
    }

    // ------------------------------------------------------------------ //
    //  Reset Handlers  (kept for programmatic use)
    // ------------------------------------------------------------------ //

    @FXML private void onResetMap()          { mapCombo.getSelectionModel().selectFirst(); }
    @FXML private void onResetRandomMap()    { randomMapCheck.setSelected(false); }
    @FXML private void onResetRandomSeed()   { randomSeedField.setText(""); }
    @FXML private void onResetRobotCount()   { robotCountSpinner.getValueFactory().setValue(DEFAULT_ROBOT_COUNT); }
    @FXML private void onResetNavAlgo()      { navAlgoCombo.getSelectionModel().select(DEFAULT_NAV_ALGO); }
    @FXML private void onResetPolicy()       { policyCombo.getSelectionModel().select(DEFAULT_POLICY); }
    @FXML private void onResetWorkloadMode() { workloadModeCombo.getSelectionModel().select(DEFAULT_WORKLOAD_MODE); }
    @FXML private void onResetSpawnRate()    { spawnRateField.setText(DEFAULT_SPAWN_RATE); }
    @FXML private void onResetMaxTasks()     { maxTasksField.setText(DEFAULT_MAX_TASKS); }
    @FXML private void onResetWorkloadSeed() { workloadSeedField.setText(DEFAULT_WORKLOAD_SEED); }
    @FXML private void onResetMaxTicks()     { maxTicksField.setText(DEFAULT_MAX_TICKS); }
    @FXML private void onResetTickMs()       { tickMsField.setText(DEFAULT_TICK_MS); }
    @FXML private void onResetRunName()      { runNameField.setText(DEFAULT_RUN_NAME); }
    @FXML private void onResetCanvasTiles()  {
        canvasWidthSpinner.getValueFactory().setValue(DEFAULT_CANVAS_TILES);
        canvasHeightSpinner.getValueFactory().setValue(DEFAULT_CANVAS_TILES);
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
                int robotCount = robotCountSpinner.getValue() != null ? robotCountSpinner.getValue() : 1;
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
            if (robotTiles.contains(key)) continue; // robot occupies this tile — skip entity
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

    /** Draw robot icons at the pre-computed tile positions (all identical robot icon). */
    private void drawPreviewRobots(GraphicsContext gc, com.openrobotics.map.Map map,
                                   double ox, double oy, double tileSize,
                                   int tileOffX, int tileOffY, Set<String> robotTiles) {
        Image robotIcon = IconLoader.getIcon("ROBOT");
        double pad = Math.max(1.0, tileSize * 0.06);
        for (String pos : robotTiles) {
            String[] parts = pos.split(",");
            int tx = Integer.parseInt(parts[0]);
            int ty = Integer.parseInt(parts[1]);
            double sx = ox + (tileOffX + tx) * tileSize;
            double sy = oy + (tileOffY + ty) * tileSize;
            if (robotIcon != null && !robotIcon.isError()) {
                gc.drawImage(robotIcon, sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            } else {
                gc.setFill(Color.web("#C2BEAE"));
                gc.fillOval(sx + pad, sy + pad, tileSize - 2*pad, tileSize - 2*pad);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Built-in Map Builders (preview only – no JSON files needed)
    // ------------------------------------------------------------------ //

    private com.openrobotics.map.Map buildBuiltinMap(String name) {
        return switch (name) {
            case "baseline_small"     -> buildBaselineSmall();
            case "narrow_aisles"      -> buildNarrowAisles();
            case "many_intersections" -> buildManyIntersections();
            default                   -> null;
        };
    }

    private com.openrobotics.map.Map buildBaselineSmall() {
        com.openrobotics.map.Map m = new com.openrobotics.map.Map(14, 10);
        int[] rackCols = {1,2,5,6,9,10};
        int[] rackRows = {1,2,6,7,8};
        for (int col : rackCols)
            for (int row : rackRows)
                m.addEntity(new Rack("rack_"+col+"_"+row, new Vector2D(col, row)));
        m.addEntity(new ChargingStation("charger",  new Vector2D(0, 4)));
        m.addEntity(new DeliveryStation("delivery", new Vector2D(13, 4)));
        return m;
    }

    private com.openrobotics.map.Map buildNarrowAisles() {
        com.openrobotics.map.Map m = new com.openrobotics.map.Map(18, 14);
        int[] rackCols = {1,2,3,4,6,7,8,9,12,13,14,15};
        int[] rackRows = {1,2,3,5,6,7,9,10,11};
        for (int col : rackCols)
            for (int row : rackRows)
                m.addEntity(new Rack("rack_"+col+"_"+row, new Vector2D(col, row)));
        m.addEntity(new ChargingStation("charger",  new Vector2D(0, 12)));
        m.addEntity(new DeliveryStation("delivery", new Vector2D(17, 12)));
        return m;
    }

    private com.openrobotics.map.Map buildManyIntersections() {
        com.openrobotics.map.Map m = new com.openrobotics.map.Map(22, 16);
        int[] colStarts = {1,4,7,10,13,16,19};
        int[] rowStarts = {1,4,7,10,13};
        for (int cs : colStarts)
            for (int rs : rowStarts)
                for (int dc = 0; dc < 2; dc++)
                    for (int dr = 0; dr < 2; dr++)
                        m.addEntity(new Rack("rack_"+(cs+dc)+"_"+(rs+dr), new Vector2D(cs+dc, rs+dr)));
        m.addEntity(new ChargingStation("charger",  new Vector2D(0, 7)));
        m.addEntity(new DeliveryStation("delivery", new Vector2D(21, 7)));
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
    //  Blocked preview click (§4.1.2 – viewport is preview only in setup)
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

    /** Opens a file chooser to pick a JSON config and initialises the engine from it. */
    @FXML
    private void onLoadConfig() {
        if (promptAndLoadConfig()) {
            goToSimulationScreen();
        }
    }

    /** Opens a file chooser to pick a save destination and writes the current engine state. */
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
        if (!AppState.hasEngine() && !AppState.hasConfigPath()) {
            File fallback = getFallbackTestConfig();
            if (fallback != null) {
                debugStatus("No loaded config in memory; using fallback test config.");
                if (!loadConfigFile(fallback)) {
                    return;
                }
            } else if (!promptAndLoadConfig()) {
                return;
            }
        }
        if (!AppState.hasEngine() && AppState.hasConfigPath()) {
            debugStatus("Rebuilding engine from saved config path.");
            SimulationEngine engine;
            try {
                engine = new SimulationEngine(AppState.getConfigPath());
            } catch (Exception e) {
                debugStatus("\u26a0 Config reload failed: " + e.getMessage());
                return;
            }
            if (engine.getMap() == null) {
                String error = engine.getInitError();
                debugStatus("\u26a0 Config reload failed: " + (error != null ? error : "unknown error"));
                return;
            }
            AppState.setEngine(engine);
        }
        if (!AppState.hasEngine()) {
            debugStatus("\u26a0 Please load a configuration file first.");
            return;
        }
        goToSimulationScreen();
    }

    /** Basic validation – returns {@code true} if all required fields are filled. */
    private boolean validate() {
        if (mapCombo.getValue() == null) {
            statusLabel.setText("⚠ Please select a map.");
            return false;
        }
        try {
            int ticks = Integer.parseInt(maxTicksField.getText().trim());
            if (ticks <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠ Max ticks must be a positive integer.");
            return false;
        }
        try {
            int tickMs = Integer.parseInt(tickMsField.getText().trim());
            if (tickMs <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠ Tick ms must be a positive integer.");
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
