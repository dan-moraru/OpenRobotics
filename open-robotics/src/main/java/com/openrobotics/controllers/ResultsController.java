package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import com.openrobotics.util.ScreenNavigator;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import java.util.List;

/**
 * Controller for ResultsScreen.fxml; displays the post-simulation results dashboard using data
 * from {@link AppState#getEngine()}.
 */
public class ResultsController {

    // Top Bar
    @FXML private Label ramLabel;
    @FXML private Label simTicksLabel;
    @FXML private Label simStatusLabel;
    @FXML private Label runNameLabel;

    // Charts
    @FXML private StackPane chartContainer1;
    @FXML private StackPane chartContainer2;

    // Robot Table
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

    // Summary Stats
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

    // Heatmap
    @FXML private StackPane heatmapContainer;
    @FXML private Canvas    heatmapCanvas;
    @FXML private Label     heatmapPlaceholder;
    @FXML private Label     maskTitleLabel;
    @FXML private Label     maskSubtitleLabel;

    // Heatmap Settings
    @FXML private CheckBox viewObjectsCheck;
    @FXML private CheckBox maskOpt1Check;

    // Initialization
    @FXML
    private void initialize() {
        // Bind the heatmap canvas to the container and redraw on resize.
        if (heatmapCanvas != null && heatmapContainer != null) {
            heatmapCanvas.widthProperty().bind(heatmapContainer.widthProperty());
            heatmapCanvas.heightProperty().bind(heatmapContainer.heightProperty());
            InvalidationListener redrawListener = e -> drawMaskCanvas();
            heatmapCanvas.widthProperty().addListener(redrawListener);
            heatmapCanvas.heightProperty().addListener(redrawListener);
        }

        updateRamLabel();

        SimulationEngine engine = AppState.getEngine();
        if (engine != null && engine.getRobots() != null) {
            populateTable(engine);
            populateCharts(engine);
            populateSummary(engine);
            if (heatmapPlaceholder != null) heatmapPlaceholder.setText("");
            drawMaskCanvas();
        }
    }

    // Top Bar
    private void updateRamLabel() {
        if (ramLabel != null) {
            long usedKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024;
            ramLabel.setText("RAM: " + usedKb + " KB");
        }
    }

    // Robot Table
    private void populateTable(SimulationEngine engine) {
        Robot[] robots = engine.getRobots();
        if (robots == null || robots.length == 0) return;

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

    // Charts
    private void populateCharts(SimulationEngine engine) {
        Robot[] robots = engine.getRobots();
        if (robots == null || robots.length == 0) return;

        // Tasks completed per robot.
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

        // Energy consumed per robot.
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

    // Summary
    private void populateSummary(SimulationEngine engine) {
        Robot[] robots = engine.getRobots();
        if (robots == null || robots.length == 0) return;

        int totalTicks = engine.getTickCounter();
        int robotCount = robots.length;

        int totalTasks = 0;
        int completedTasks = 0;
        int pendingTasks = 0;
        if (engine.getDispatcher() != null) {
            List<Task> allTasks = engine.getDispatcher().getAllQueuedTasks();
            pendingTasks = (int) allTasks.stream()
                    .filter(task -> task.getStatus() != TaskStatus.COMPLETED)
                    .count();
            totalTasks = engine.getDispatcher().getTotalTasksAdded();
        }

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

        // Top bar.
        if (simTicksLabel != null) simTicksLabel.setText("Ticks: " + totalTicks);
        if (simStatusLabel != null) {
            simStatusLabel.setText(engine.getIsRunning() ? "Status: Running" : "Status: Stopped");
        }
        if (runNameLabel != null) {
            runNameLabel.setText(AppState.getConfigPath() != null
                    ? AppState.getConfigPath() : "");
        }

        // Simulation summary column.
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

        // Robot averages column.
        setText(statAvgTasks, String.format("Avg Tasks/Robot: %.1f", (double) sumTasks / robotCount));
        setText(statAvgDistance, String.format("Avg Distance: %.1f tiles", (double) sumDist / robotCount));
        setText(statAvgEnergy, String.format("Avg Energy Used: %.1f", sumEnergy / robotCount));
        if (totalTicks > 0) {
            setText(statAvgIdlePct, String.format("Avg Idle: %.1f%%", 100.0 * sumIdle / (totalTicks * robotCount)));
            setText(statAvgStuckPct, String.format("Avg Stuck: %.1f%%", 100.0 * sumStuck / (totalTicks * robotCount)));
        }
        setText(statAvgBattery, String.format("Avg Battery: %.0f%%", sumBattery / robotCount));

        // Highlights column.
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

    // Heatmap Rendering
    private void drawMaskCanvas() {
        if (heatmapCanvas == null) return;
        var gc = heatmapCanvas.getGraphicsContext2D();
        double w = heatmapCanvas.getWidth();
        double h = heatmapCanvas.getHeight();

        // Background: lighter than the viewport so unvisited tiles stand out.
        gc.setFill(javafx.scene.paint.Color.web("#E8E4DB"));
        gc.fillRect(0, 0, w, h);

        SimulationEngine engine = AppState.getEngine();
        if (engine == null || engine.getMap() == null) return;

        int mapW = engine.getMap().getWidth();
        int mapH = engine.getMap().getHeight();
        double tileSize = Math.min(w / mapW, h / mapH);
        double offsetX = (w - mapW * tileSize) / 2;
        double offsetY = (h - mapH * tileSize) / 2;

        int maxVisits = 0;
        for (int y = 0; y < mapH; y++) {
            for (int x = 0; x < mapW; x++) {
                Tile tile = engine.getMap().getTile(x, y);
                if (tile != null) {
                    int visits = tile.getVisitCount();
                    if (visits > maxVisits) {
                        maxVisits = visits;
                    }
                }
            }
        }

        // Use low, medium, and high traffic bands based on the maximum visit count.
        int lowThreshold  = maxVisits > 0 ? Math.max(1, (int) Math.ceil(maxVisits * 0.33)) : 0;
        int mediumThreshold = maxVisits > 0 ? Math.max(1, (int) Math.ceil(maxVisits * 0.66)) : 0;

        for (int y = 0; y < mapH; y++) {
            for (int x = 0; x < mapW; x++) {
                Tile tile = engine.getMap().getTile(x, y);
                if (tile != null) {
                    int visits = tile.getVisitCount();
                    javafx.scene.paint.Color color;
                    if (visits == 0) {
                        // Unvisited tiles.
                        color = javafx.scene.paint.Color.web("#F4F1EA");
                    } else if (visits >= mediumThreshold) {
                        // High traffic.
                        color = javafx.scene.paint.Color.web("#C0392B");
                    } else if (visits >= lowThreshold) {
                        // Medium traffic.
                        color = javafx.scene.paint.Color.web("#e89003");
                    } else {
                        // Low traffic.
                        color = javafx.scene.paint.Color.web("#fffaa2");
                    }
                    gc.setFill(color);
                    gc.fillRect(offsetX + x * tileSize, offsetY + y * tileSize, tileSize - 1, tileSize - 1);
                }
            }
        }

        if (viewObjectsCheck != null && viewObjectsCheck.isSelected()) {
            drawEntitiesOverlay(gc, engine, tileSize, offsetX, offsetY);
        }

        // Map boundary.
        gc.setStroke(javafx.scene.paint.Color.web("#5D5B54"));
        gc.setLineWidth(1.5);
        gc.strokeRect(offsetX, offsetY, mapW * tileSize, mapH * tileSize);
    }

    private void drawEntitiesOverlay(GraphicsContext gc, SimulationEngine engine, double tileSize, double offsetX, double offsetY) {
        if (engine.getMap() == null) return;

        // Obstacles.
        gc.setFill(javafx.scene.paint.Color.web("#2A2926"));
        for (MapEntity entity : engine.getMap().getEntities()) {
            if (entity instanceof Obstacle) {
                double px = offsetX + entity.getPosition().getX() * tileSize + 1;
                double py = offsetY + entity.getPosition().getY() * tileSize + 1;
                gc.fillRect(px, py, tileSize - 2, tileSize - 2);
            }
        }

        // Charging stations.
        gc.setFill(javafx.scene.paint.Color.web("#8C7B38"));
        for (MapEntity entity : engine.getMap().getEntities()) {
            if (entity instanceof ChargingStation) {
                double px = offsetX + entity.getPosition().getX() * tileSize + 1;
                double py = offsetY + entity.getPosition().getY() * tileSize + 1;
                gc.fillRect(px, py, tileSize - 2, tileSize - 2);
            }
        }

        // Delivery stations.
        gc.setFill(javafx.scene.paint.Color.web("#3A5A8C"));
        for (MapEntity entity : engine.getMap().getEntities()) {
            if (entity instanceof DeliveryStation) {
                double px = offsetX + entity.getPosition().getX() * tileSize + 1;
                double py = offsetY + entity.getPosition().getY() * tileSize + 1;
                gc.fillRect(px, py, tileSize - 2, tileSize - 2);
            }
        }

        // Racks.
        gc.setFill(javafx.scene.paint.Color.web("#6E6B65"));
        for (MapEntity entity : engine.getMap().getEntities()) {
            if (entity instanceof Rack) {
                double px = offsetX + entity.getPosition().getX() * tileSize + 1;
                double py = offsetY + entity.getPosition().getY() * tileSize + 1;
                gc.fillRect(px, py, tileSize - 2, tileSize - 2);
            }
        }

        // Robots at their final positions, colored by state.
        Robot[] robots = engine.getRobots();
        if (robots != null) {
            for (Robot robot : robots) {
                double px = offsetX + robot.getPosition().getX() * tileSize + tileSize / 2;
                double py = offsetY + robot.getPosition().getY() * tileSize + tileSize / 2;
                double radius = Math.max(3, tileSize / 4);

                switch (robot.getState()) {
                    case MOVING -> gc.setFill(javafx.scene.paint.Color.web("#599068"));
                    case CHARGING -> gc.setFill(javafx.scene.paint.Color.web("#8C7B38"));
                    case LOADING, UNLOADING -> gc.setFill(javafx.scene.paint.Color.web("#706E65"));
                    default -> gc.setFill(javafx.scene.paint.Color.web("#8D8A7F"));
                }
                gc.fillOval(px - radius, py - radius, radius * 2, radius * 2);
            }
        }
    }

    // Navigation
    @FXML private void onTabEditor()  { ScreenNavigator.goToSimulation(); }
    @FXML private void onTabResults() { }

    @FXML private void onMaskZoomIn()  { }
    @FXML private void onMaskZoomOut() { }

    @FXML
    private void onToggleViewObjects() {
        drawMaskCanvas();
    }

    @FXML
    private void onNewExperiment() { ScreenNavigator.goToSetup(); }

    @FXML
    private void onViewHistory() { ScreenNavigator.goToSimulation(); }

    @FXML
    private void onExport() {
        ScreenNavigator.openDialog(ScreenNavigator.DIALOG_EXPORT_RESULTS, "Export Results");
    }

    @FXML private void onMenuWelcome() { ScreenNavigator.goToWelcome(); }

    @FXML
    private void onMenuGithub() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/dan-moraru/OpenRobotics"));
        } catch (Exception ex) { /* ignore */ }
    }

    @FXML
    private void onExit() {
        if (ScreenNavigator.confirmExit()) javafx.application.Platform.exit();
    }
}
