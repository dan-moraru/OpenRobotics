package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import com.openrobotics.util.ScreenNavigator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Controller for {@code ResultsScreen.fxml}.
 *
 * <p>Displays the post-simulation results dashboard with real statistics
 * pulled from the active {@link SimulationEngine} in {@link AppState}.
 */
public class ResultsController {

    // ── TOP BAR ─────────────────────────────────────────────────────────
    @FXML private Label ramLabel;
    @FXML private Label simTicksLabel;
    @FXML private Label simStatusLabel;
    @FXML private Label runNameLabel;

    // ── CHARTS ──────────────────────────────────────────────────────────
    @FXML private StackPane chartContainer1;
    @FXML private StackPane chartContainer2;

    // ── TABLE ───────────────────────────────────────────────────────────
    @FXML private TableView<Robot>          robotStatsTable;
    @FXML private TableColumn<Robot,String> colRobotId;
    @FXML private TableColumn<Robot,String> colNavAlgo;
    @FXML private TableColumn<Robot,String> colTasksDone;
    @FXML private TableColumn<Robot,String> colDistance;
    @FXML private TableColumn<Robot,String> colEnergy;
    @FXML private TableColumn<Robot,String> colIdleTicks;
    @FXML private TableColumn<Robot,String> colWaitTicks;
    @FXML private TableColumn<Robot,String> colBattery;
    @FXML private TableColumn<Robot,String> colState;
    @FXML private Label     tableTitle;
    @FXML private Label     tableInfoLabel;

    // ── SUMMARY STATS ────────────────────────────────────────────────────
    @FXML private Label statTotalTicks;
    @FXML private Label statTotalTasks;
    @FXML private Label statCompletedTasks;
    @FXML private Label statPendingTasks;
    @FXML private Label statThroughput;
    @FXML private Label statCompletionRate;

    @FXML private Label statAvgTasks;
    @FXML private Label statAvgDistance;
    @FXML private Label statAvgEnergy;
    @FXML private Label statAvgIdlePct;
    @FXML private Label statAvgStuckPct;
    @FXML private Label statAvgBattery;

    @FXML private Label statBestRobot;
    @FXML private Label statWorstRobot;
    @FXML private Label statMostDistance;
    @FXML private Label statMostEnergy;
    @FXML private Label statMostIdle;
    @FXML private Label statTotalDistance;

    // ── MASK VIEWPORT ───────────────────────────────────────────────────
    @FXML private StackPane heatmapContainer;
    @FXML private Canvas    heatmapCanvas;
    @FXML private Label     heatmapPlaceholder;
    @FXML private Label     maskTitleLabel;
    @FXML private Label     maskSubtitleLabel;

    // ── MASK SETTINGS ────────────────────────────────────────────────────
    @FXML private CheckBox viewObjectsCheck;
    @FXML private CheckBox maskOpt1Check;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        // Bind mask canvas size
        if (heatmapCanvas != null && heatmapContainer != null) {
            heatmapCanvas.widthProperty().bind(heatmapContainer.widthProperty());
            heatmapCanvas.heightProperty().bind(heatmapContainer.heightProperty());
            heatmapCanvas.widthProperty().addListener(e  -> drawMaskCanvas());
            heatmapCanvas.heightProperty().addListener(e -> drawMaskCanvas());
        }

        // Update RAM display
        updateRamLabel();

        // Load real data from engine
        SimulationEngine engine = AppState.getEngine();
        if (engine != null && engine.getRobots() != null) {
            populateTable(engine);
            populateCharts(engine);
            populateSummary(engine);
            if (heatmapPlaceholder != null) heatmapPlaceholder.setText("");
        }
    }

    private void updateRamLabel() {
        if (ramLabel != null) {
            long usedKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024;
            ramLabel.setText("RAM: " + usedKb + " KB");
        }
    }

    // ------------------------------------------------------------------ //
    //  Table population
    // ------------------------------------------------------------------ //

    private void populateTable(SimulationEngine engine) {
        Robot[] robots = engine.getRobots();
        if (robots == null || robots.length == 0) return;

        // Configure columns with cell value factories
        colRobotId.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getName()));
        colNavAlgo.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getNav() != null
                        ? cd.getValue().getNav().getClass().getSimpleName()
                                .replace("NavigationStrategy", "")
                        : "None"));
        colTasksDone.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().getTasksCompleted())));
        colDistance.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().getTotalDistanceMoved())));
        colEnergy.setCellValueFactory(cd ->
                new SimpleStringProperty(String.format("%.1f", cd.getValue().getTotalEnergyConsumed())));
        colIdleTicks.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().getTotalIdleTicks())));
        colWaitTicks.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().getStuckTicks())));
        colBattery.setCellValueFactory(cd ->
                new SimpleStringProperty(String.format("%.0f%%", cd.getValue().getBattery())));
        colState.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getState().name()));

        ObservableList<Robot> data = FXCollections.observableArrayList(robots);
        robotStatsTable.setItems(data);

        if (tableInfoLabel != null) {
            tableInfoLabel.setText("robots: " + robots.length);
        }
    }

    // ------------------------------------------------------------------ //
    //  Chart population
    // ------------------------------------------------------------------ //

    private void populateCharts(SimulationEngine engine) {
        Robot[] robots = engine.getRobots();
        if (robots == null || robots.length == 0) return;

        // Chart 1: Tasks completed per robot (bar chart)
        if (chartContainer1 != null) {
            chartContainer1.getChildren().clear();
            CategoryAxis xAxis1 = new CategoryAxis();
            xAxis1.setLabel("Robot");
            NumberAxis yAxis1 = new NumberAxis();
            yAxis1.setLabel("Tasks");
            BarChart<String, Number> tasksChart = new BarChart<>(xAxis1, yAxis1);
            tasksChart.setLegendVisible(false);
            tasksChart.setAnimated(false);
            tasksChart.getStyleClass().add("results-chart");

            XYChart.Series<String, Number> tasksSeries = new XYChart.Series<>();
            tasksSeries.setName("Tasks");
            for (Robot r : robots) {
                String label = r.getName().length() > 8
                        ? r.getName().substring(0, 7) + "\u2026" : r.getName();
                tasksSeries.getData().add(new XYChart.Data<>(label, r.getTasksCompleted()));
            }
            tasksChart.getData().add(tasksSeries);
            chartContainer1.getChildren().add(tasksChart);
        }

        // Chart 2: Energy consumed per robot (bar chart)
        if (chartContainer2 != null) {
            chartContainer2.getChildren().clear();
            CategoryAxis xAxis2 = new CategoryAxis();
            xAxis2.setLabel("Robot");
            NumberAxis yAxis2 = new NumberAxis();
            yAxis2.setLabel("Energy");
            BarChart<String, Number> energyChart = new BarChart<>(xAxis2, yAxis2);
            energyChart.setLegendVisible(false);
            energyChart.setAnimated(false);
            energyChart.getStyleClass().add("results-chart");

            XYChart.Series<String, Number> energySeries = new XYChart.Series<>();
            energySeries.setName("Energy");
            for (Robot r : robots) {
                String label = r.getName().length() > 8
                        ? r.getName().substring(0, 7) + "\u2026" : r.getName();
                energySeries.getData().add(new XYChart.Data<>(label, r.getTotalEnergyConsumed()));
            }
            energyChart.getData().add(energySeries);
            chartContainer2.getChildren().add(energyChart);
        }
    }

    // ------------------------------------------------------------------ //
    //  Summary statistics
    // ------------------------------------------------------------------ //

    private void populateSummary(SimulationEngine engine) {
        Robot[] robots = engine.getRobots();
        if (robots == null || robots.length == 0) return;

        int totalTicks = engine.getTickCounter();
        int robotCount = robots.length;

        // Count tasks from dispatcher
        int totalTasks = 0;
        int completedTasks = 0;
        int pendingTasks = 0;
        if (engine.getDispatcher() != null) {
            List<Task> allTasks = engine.getDispatcher().getAllTasks();
            pendingTasks = allTasks.size();
            totalTasks = engine.getDispatcher().getTotalTasksAdded();
        }

        // Accumulate per-robot stats
        int sumTasks = 0, sumDist = 0, sumIdle = 0, sumStuck = 0, sumMoving = 0, sumCharging = 0;
        float sumEnergy = 0, sumBattery = 0;
        Robot bestRobot = robots[0], worstRobot = robots[0];
        Robot mostDistRobot = robots[0], mostEnergyRobot = robots[0], mostIdleRobot = robots[0];

        for (Robot r : robots) {
            sumTasks += r.getTasksCompleted();
            sumDist += r.getTotalDistanceMoved();
            sumEnergy += r.getTotalEnergyConsumed();
            sumIdle += r.getTotalIdleTicks();
            sumStuck += r.getStuckTicks();
            sumMoving += r.getTotalMovingTicks();
            sumCharging += r.getTotalChargingTicks();
            sumBattery += r.getBattery();

            if (r.getTasksCompleted() > bestRobot.getTasksCompleted()) bestRobot = r;
            if (r.getTasksCompleted() < worstRobot.getTasksCompleted()) worstRobot = r;
            if (r.getTotalDistanceMoved() > mostDistRobot.getTotalDistanceMoved()) mostDistRobot = r;
            if (r.getTotalEnergyConsumed() > mostEnergyRobot.getTotalEnergyConsumed()) mostEnergyRobot = r;
            if (r.getTotalIdleTicks() > mostIdleRobot.getTotalIdleTicks()) mostIdleRobot = r;
        }
        completedTasks = sumTasks;
        totalTasks = Math.max(totalTasks, completedTasks + pendingTasks);

        // Top bar
        if (simTicksLabel != null) simTicksLabel.setText("Ticks: " + totalTicks);
        if (simStatusLabel != null) {
            simStatusLabel.setText(engine.getIsRunning() ? "Status: Running" : "Status: Stopped");
        }
        if (runNameLabel != null) {
            runNameLabel.setText(AppState.getConfigPath() != null
                    ? AppState.getConfigPath() : "");
        }

        // Simulation summary column
        setText(statTotalTicks, "Total Ticks: " + totalTicks);
        setText(statTotalTasks, "Total Tasks: " + totalTasks);
        setText(statCompletedTasks, "Completed: " + completedTasks);
        setText(statPendingTasks, "Pending: " + pendingTasks);
        if (totalTicks > 0) {
            setText(statThroughput, String.format("Throughput: %.3f tasks/tick", (double) completedTasks / totalTicks));
        } else {
            setText(statThroughput, "Throughput: –");
        }
        if (totalTasks > 0) {
            setText(statCompletionRate, String.format("Completion: %.1f%%", 100.0 * completedTasks / totalTasks));
        } else {
            setText(statCompletionRate, "Completion: –");
        }

        // Robot averages column
        setText(statAvgTasks, String.format("Avg Tasks/Robot: %.1f", (double) sumTasks / robotCount));
        setText(statAvgDistance, String.format("Avg Distance: %.1f tiles", (double) sumDist / robotCount));
        setText(statAvgEnergy, String.format("Avg Energy Used: %.1f", sumEnergy / robotCount));
        if (totalTicks > 0) {
            setText(statAvgIdlePct, String.format("Avg Idle: %.1f%%", 100.0 * sumIdle / (totalTicks * robotCount)));
            setText(statAvgStuckPct, String.format("Avg Stuck: %.1f%%", 100.0 * sumStuck / (totalTicks * robotCount)));
        }
        setText(statAvgBattery, String.format("Avg Battery: %.0f%%", sumBattery / robotCount));

        // Highlights column
        setText(statBestRobot, "Most Tasks: " + bestRobot.getName() + " (" + bestRobot.getTasksCompleted() + ")");
        setText(statWorstRobot, "Fewest Tasks: " + worstRobot.getName() + " (" + worstRobot.getTasksCompleted() + ")");
        setText(statMostDistance, "Most Distance: " + mostDistRobot.getName()
                + " (" + mostDistRobot.getTotalDistanceMoved() + ")");
        setText(statMostEnergy, String.format("Most Energy: %s (%.1f)",
                mostEnergyRobot.getName(), mostEnergyRobot.getTotalEnergyConsumed()));
        setText(statMostIdle, "Most Idle: " + mostIdleRobot.getName()
                + " (" + mostIdleRobot.getTotalIdleTicks() + " tks)");
        setText(statTotalDistance, "Total Distance: " + sumDist + " tiles");
    }

    private void setText(Label label, String text) {
        if (label != null) label.setText(text);
    }

    // ------------------------------------------------------------------ //
    //  Heatmap canvas
    // ------------------------------------------------------------------ //

    private void drawMaskCanvas() {
        if (heatmapCanvas == null) return;
        var gc = heatmapCanvas.getGraphicsContext2D();
        double w = heatmapCanvas.getWidth();
        double h = heatmapCanvas.getHeight();
        gc.setFill(javafx.scene.paint.Color.web("#CDCBC3"));
        gc.fillRect(0, 0, w, h);

        // Draw grid lines
        SimulationEngine engine = AppState.getEngine();
        if (engine == null || engine.getMap() == null) return;

        int mapW = engine.getMap().getWidth();
        int mapH = engine.getMap().getHeight();
        double tileSize = Math.min(w / mapW, h / mapH);
        double offsetX = (w - mapW * tileSize) / 2;
        double offsetY = (h - mapH * tileSize) / 2;

        // Grid
        gc.setStroke(javafx.scene.paint.Color.web("#B0ADA5"));
        gc.setLineWidth(0.5);
        for (int x = 0; x <= mapW; x++) {
            gc.strokeLine(offsetX + x * tileSize, offsetY, offsetX + x * tileSize, offsetY + mapH * tileSize);
        }
        for (int y = 0; y <= mapH; y++) {
            gc.strokeLine(offsetX, offsetY + y * tileSize, offsetX + mapW * tileSize, offsetY + y * tileSize);
        }

        // Map boundary
        gc.setStroke(javafx.scene.paint.Color.web("#5D5B54"));
        gc.setLineWidth(1.5);
        gc.strokeRect(offsetX, offsetY, mapW * tileSize, mapH * tileSize);
    }

    // ------------------------------------------------------------------ //
    //  Tab strip
    // ------------------------------------------------------------------ //

    @FXML private void onTabEditor()  { ScreenNavigator.goToSimulation(); }
    @FXML private void onTabResults() { /* already here */ }

    // ------------------------------------------------------------------ //
    //  Mask viewport
    // ------------------------------------------------------------------ //

    @FXML private void onMaskZoomIn()  { }
    @FXML private void onMaskZoomOut() { }

    @FXML
    private void onToggleViewObjects() {
        drawMaskCanvas();
    }

    // ------------------------------------------------------------------ //
    //  Bottom actions
    // ------------------------------------------------------------------ //

    @FXML
    private void onNewExperiment() { ScreenNavigator.goToSetup(); }

    @FXML
    private void onViewHistory() { ScreenNavigator.goToSimulation(); }

    @FXML
    private void onExport() {
        ScreenNavigator.openDialog(ScreenNavigator.DIALOG_SAVE_CONFIG, "Export Results");
    }

    // ------------------------------------------------------------------ //
    //  Menu
    // ------------------------------------------------------------------ //

    @FXML private void onMenuWelcome() { ScreenNavigator.goToWelcome(); }

    @FXML
    private void onMenuGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com"));
        } catch (Exception ex) { /* ignore */ }
    }

    @FXML
    private void onExit() {
        if (ScreenNavigator.confirmExit()) javafx.application.Platform.exit();
    }
}
