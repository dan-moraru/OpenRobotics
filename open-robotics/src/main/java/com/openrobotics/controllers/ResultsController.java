package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller for {@code ResultsScreen.fxml}.
 *
 * <p>Displays the post-simulation results dashboard (§4.1.3 Results).
 */
public class ResultsController {

    // ── TOP BAR ─────────────────────────────────────────────────────────
    @FXML private Label ramLabel;

    // ── CHARTS ──────────────────────────────────────────────────────────
    @FXML private StackPane chartContainer1;
    @FXML private StackPane chartContainer2;
    @FXML private Label     chartPageLabel;
    @FXML private Label     chartPage2Label;
    private int currentChartPage  = 0;
    private int currentChartPage2 = 1;
    private int totalChartPages   = 10;

    // ── TABLE ───────────────────────────────────────────────────────────
    @FXML private TableView<Object>          robotStatsTable;
    @FXML private TableColumn<Object,String> colRobotId;
    @FXML private TableColumn<Object,String> colNavAlgo;
    @FXML private TableColumn<Object,String> colTasksDone;
    @FXML private TableColumn<Object,String> colDistance;
    @FXML private TableColumn<Object,String> colEnergy;
    @FXML private TableColumn<Object,String> colIdleTicks;
    @FXML private TableColumn<Object,String> colWaitTicks;
    @FXML private TableColumn<Object,String> colRobotCollisions;
    @FXML private TableColumn<Object,String> colDeadlocks;
    @FXML private Label     tableTitle;
    @FXML private Label     tableInfoLabel;
    @FXML private Label     tablePageLabel;
    private int currentTablePage = 0;

    // ── DISPLAY SETTINGS ────────────────────────────────────────────────
    @FXML private Spinner<Integer> columnCountSpinner;
    @FXML private CheckBox         showAllRobotsCheck;
    @FXML private CheckBox         showEnergyCheck;
    @FXML private CheckBox         showCollisionsCheck;
    @FXML private CheckBox         showDeadlocksCheck;

    // ── OPT SUMMARY COLUMNS ─────────────────────────────────────────────
    @FXML private VBox optSummaryCol1;
    @FXML private VBox optSummaryCol2;
    @FXML private VBox optSummaryCol3;

    // ── MASK VIEWPORT ───────────────────────────────────────────────────
    @FXML private StackPane heatmapContainer;
    @FXML private Canvas    heatmapCanvas;
    @FXML private Label     maskPageLabel;
    @FXML private Label     maskTitleLabel;
    @FXML private Label     maskSubtitleLabel;

    // ── MASK SETTINGS ────────────────────────────────────────────────────
    @FXML private CheckBox viewObjectsCheck;
    @FXML private CheckBox maskOpt1Check;
    @FXML private CheckBox maskOpt2Check;

    // ------------------------------------------------------------------ //
    //  Initialisation
    // ------------------------------------------------------------------ //

    @FXML
    private void initialize() {
        if (chartPageLabel  != null) chartPageLabel.setText("1/" + totalChartPages);
        if (chartPage2Label != null) chartPage2Label.setText("2/" + totalChartPages);
        if (tablePageLabel  != null) tablePageLabel.setText("1/6");
        if (maskPageLabel   != null) maskPageLabel.setText("1/3");
        if (ramLabel        != null) ramLabel.setText("ram: – kb");

        // Bind mask canvas size
        if (heatmapCanvas != null && heatmapContainer != null) {
            heatmapCanvas.widthProperty().bind(heatmapContainer.widthProperty());
            heatmapCanvas.heightProperty().bind(heatmapContainer.heightProperty());
            heatmapCanvas.widthProperty().addListener(e  -> drawMaskCanvas());
            heatmapCanvas.heightProperty().addListener(e -> drawMaskCanvas());
        }

        // TODO Sprint 6: load results for the most recent completed run from DB
    }

    private void drawMaskCanvas() {
        if (heatmapCanvas == null) return;
        var gc = heatmapCanvas.getGraphicsContext2D();
        double w = heatmapCanvas.getWidth();
        double h = heatmapCanvas.getHeight();
        gc.setFill(javafx.scene.paint.Color.web("#CDCBC3"));
        gc.fillRect(0, 0, w, h);
        // TODO Sprint 6: render heatmap mask
    }

    // ------------------------------------------------------------------ //
    //  Tab strip
    // ------------------------------------------------------------------ //

    @FXML private void onTabEditor()  { ScreenNavigator.goToSimulation(); }
    @FXML private void onTabResults() { /* already here */ }

    // ------------------------------------------------------------------ //
    //  Chart 1 pagination
    // ------------------------------------------------------------------ //

    @FXML
    private void onPrevChart() {
        if (currentChartPage > 0) { currentChartPage--; updateChartPage(); }
    }

    @FXML
    private void onNextChart() {
        if (currentChartPage < totalChartPages - 1) { currentChartPage++; updateChartPage(); }
    }

    private void updateChartPage() {
        if (chartPageLabel != null)
            chartPageLabel.setText((currentChartPage + 1) + "/" + totalChartPages);
    }

    // ------------------------------------------------------------------ //
    //  Chart 2 pagination
    // ------------------------------------------------------------------ //

    @FXML
    private void onPrevChart2() {
        if (currentChartPage2 > 0) { currentChartPage2--; updateChartPage2(); }
    }

    @FXML
    private void onNextChart2() {
        if (currentChartPage2 < totalChartPages - 1) { currentChartPage2++; updateChartPage2(); }
    }

    private void updateChartPage2() {
        if (chartPage2Label != null)
            chartPage2Label.setText((currentChartPage2 + 1) + "/" + totalChartPages);
    }

    // ------------------------------------------------------------------ //
    //  Table pagination
    // ------------------------------------------------------------------ //

    @FXML
    private void onPrevTable() {
        if (currentTablePage > 0) { currentTablePage--; updateTablePage(); }
    }

    @FXML
    private void onNextTable() {
        currentTablePage++;
        updateTablePage();
    }

    private void updateTablePage() {
        if (tablePageLabel != null)
            tablePageLabel.setText((currentTablePage + 1) + "/6");
    }

    // ------------------------------------------------------------------ //
    //  Mask viewport
    // ------------------------------------------------------------------ //

    @FXML private void onMaskZoomIn()  { /* TODO Sprint 6: zoom mask canvas */ }
    @FXML private void onMaskZoomOut() { /* TODO Sprint 6: zoom mask canvas */ }

    @FXML
    private void onPrevMask() {
        if (maskPageLabel != null) maskPageLabel.setText("prev mask");
    }

    @FXML
    private void onNextMask() {
        if (maskPageLabel != null) maskPageLabel.setText("next mask");
    }

    @FXML
    private void onToggleViewObjects() {
        // TODO Sprint 6: show/hide objects on mask viewport
    }

    // ------------------------------------------------------------------ //
    //  Bottom actions
    // ------------------------------------------------------------------ //

    @FXML
    private void onNewExperiment() { ScreenNavigator.goToSetup(); }

    @FXML
    private void onViewHistory() {
        // TODO Sprint 6: open history dialog
    }

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
