package com.openrobotics.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrobotics.AppState;
import com.openrobotics.io.ResultsExportDTO;
import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.robot.navigation.NavigationStrategy;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.MoveIntention;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import com.openrobotics.util.ScreenNavigator;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportResultsControllerTest extends ApplicationTest {

    private static final String PREFS_KEY = "recentExportDirs";
    private static final int MAX_RECENT = 8;

    static {
        System.setProperty("java.util.prefs.PreferencesFactory",
                InMemoryPreferencesFactory.class.getName());
    }

    @TempDir
    Path tempDir;

    private ExportResultsController controller;
    private Stage dialogStage;

    @Override
    public void start(Stage stage) throws Exception {
        dialogStage = stage;
        ScreenNavigator.setPrimaryStage(stage);
        clearPrefs();
        loadDialog();
    }

    @AfterEach
    void tearDown() throws Exception {
        AppState.clear();
        clearPrefs();
        if (dialogStage != null && dialogStage.isShowing()) {
            interact(dialogStage::close);
            WaitForAsyncUtils.waitForFxEvents();
        }
    }

    @Test
    void initialize_without_recent_dirs_sets_default_filename_and_no_selection() {
        assertAll(
                () -> assertTrue(directoryCombo().getItems().isEmpty()),
                () -> assertEquals("No directory selected", selectedDirLabel().getText()),
                () -> assertEquals("results_" + LocalDate.now() + ".json", fileNameField().getText())
        );
    }

    @Test
    void initialize_loads_recent_dirs_from_preferences_and_selects_first() throws Exception {
        Path first = Files.createDirectory(tempDir.resolve("first"));
        Path second = Files.createDirectory(tempDir.resolve("second"));
        writeRecentDirs(first.toString(), second.toString(), tempDir.resolve("ignored-nine").toString(),
                tempDir.resolve("ignored-eight").toString(), tempDir.resolve("four").toString(),
                tempDir.resolve("five").toString(), tempDir.resolve("six").toString(),
                tempDir.resolve("seven").toString(), tempDir.resolve("not-loaded").toString());

        reloadDialog();

        assertAll(
                () -> assertEquals(MAX_RECENT, directoryCombo().getItems().size()),
                () -> assertEquals(first.toString(), directoryCombo().getSelectionModel().getSelectedItem()),
                () -> assertEquals(first.toString(), selectedDirLabel().getText())
        );
    }

    @Test
    void selecting_directory_combo_value_updates_label_and_selected_directory() throws Exception {
        Path exportDir = Files.createDirectory(tempDir.resolve("exports"));

        chooseDirectory(exportDir);

        assertEquals(exportDir.toString(), selectedDirLabel().getText());
    }

    @Test
    void reset_directory_selects_default_documents_directory_and_adds_it_to_combo() throws Exception {
        invokePrivate("onResetDirectory");

        String expected = System.getProperty("user.home") + File.separator + "Documents";
        assertAll(
                () -> assertEquals(expected, selectedDirLabel().getText()),
                () -> assertEquals(expected, directoryCombo().getSelectionModel().getSelectedItem()),
                () -> assertTrue(directoryCombo().getItems().contains(expected))
        );
    }

    @Test
    void export_without_directory_shows_error_and_keeps_dialog_open() throws Exception {
        setFileName("results.json");

        invokePrivate("onExport");

        assertAll(
                () -> assertEquals("Select a directory before exporting.", selectedDirLabel().getText()),
                () -> assertTrue(dialogStage.isShowing())
        );
    }

    @Test
    void export_rejects_blank_filename_before_touching_engine() throws Exception {
        chooseDirectory(Files.createDirectory(tempDir.resolve("exports")));
        setFileName("   ");
        AppState.setEngine(reportingEngine(10, new Robot[0], new Dispatcher()));

        invokePrivate("onExport");

        assertAll(
                () -> assertEquals("File name cannot be empty.", selectedDirLabel().getText()),
                () -> assertFalse(Files.exists(tempDir.resolve("exports").resolve(".json"))),
                () -> assertTrue(dialogStage.isShowing())
        );
    }

    @Test
    void export_rejects_invalid_filename_characters() throws Exception {
        chooseDirectory(Files.createDirectory(tempDir.resolve("exports")));
        setFileName("bad:name.json");
        AppState.setEngine(reportingEngine(10, new Robot[0], new Dispatcher()));

        invokePrivate("onExport");

        assertEquals("File name contains invalid characters.", selectedDirLabel().getText());
    }

    @Test
    void export_rejects_missing_directory() throws Exception {
        Path missingDir = tempDir.resolve("missing");
        chooseDirectory(missingDir);
        setFileName("results.json");
        AppState.setEngine(reportingEngine(10, new Robot[0], new Dispatcher()));

        invokePrivate("onExport");

        assertEquals("Selected directory does not exist.", selectedDirLabel().getText());
    }

    @Test
    void export_rejects_when_no_simulation_engine_is_available() throws Exception {
        chooseDirectory(Files.createDirectory(tempDir.resolve("exports")));
        setFileName("results.json");
        AppState.clear();

        invokePrivate("onExport");

        assertEquals("No simulation data available to export.", selectedDirLabel().getText());
    }

    @Test
    void export_writes_json_closes_dialog_and_saves_recent_directory() throws Exception {
        Path exportDir = Files.createDirectory(tempDir.resolve("exports"));
        Dispatcher dispatcher = dispatcherWithPendingTasks(2, 100);
        Robot[] robots = {
                new ReportingRobot("R-1", new AuditNavigationStrategy(), 3, 14, 12.5f,
                        4, 2, 87.5f, RobotState.MOVING),
                new ReportingRobot("R-2", null, 2, 9, 7.25f,
                        8, 0, 64.0f, RobotState.CHARGING)
        };
        AppState.setConfigPath("configs/run-a.json");
        AppState.setEngine(reportingEngine(50, robots, dispatcher));
        chooseDirectory(exportDir);
        setFileName("summary");

        invokePrivate("onExport");

        Path output = exportDir.resolve("summary.json");
        JsonNode json = new ObjectMapper().readTree(output.toFile());
        assertAll(
                () -> assertFalse(dialogStage.isShowing()),
                () -> assertTrue(Files.size(output) > 0),
                () -> assertEquals("configs/run-a.json", json.get("runName").asText()),
                () -> assertEquals(50, json.get("totalTicks").asInt()),
                () -> assertEquals(7, json.get("totalTasks").asInt()),
                () -> assertEquals(5, json.get("completedTasks").asInt()),
                () -> assertEquals(2, json.get("pendingTasks").asInt()),
                () -> assertEquals(0.1, json.get("throughput").asDouble(), 0.0001),
                () -> assertEquals(71.4285, json.get("completionRate").asDouble(), 0.0001),
                () -> assertEquals(2, json.get("robots").size()),
                () -> assertEquals("R-1", json.get("robots").get(0).get("name").asText()),
                () -> assertEquals("Audit", json.get("robots").get(0).get("navigationAlgorithm").asText()),
                () -> assertEquals(3, json.get("robots").get(0).get("tasksCompleted").asInt()),
                () -> assertEquals(14, json.get("robots").get(0).get("totalDistanceMoved").asInt()),
                () -> assertEquals(12.5, json.get("robots").get(0).get("totalEnergyConsumed").asDouble(), 0.0001),
                () -> assertEquals(4, json.get("robots").get(0).get("totalIdleTicks").asInt()),
                () -> assertEquals(2, json.get("robots").get(0).get("stuckTicks").asInt()),
                () -> assertEquals(87.5, json.get("robots").get(0).get("battery").asDouble(), 0.0001),
                () -> assertEquals("MOVING", json.get("robots").get(0).get("finalState").asText()),
                () -> assertEquals("None", json.get("robots").get(1).get("navigationAlgorithm").asText()),
                () -> assertEquals(exportDir.toString(), prefs().get(PREFS_KEY + 0, null))
        );
    }

    @Test
    void export_appends_json_extension_and_avoids_overwriting_existing_file() throws Exception {
        Path exportDir = Files.createDirectory(tempDir.resolve("exports"));
        Path existing = exportDir.resolve("report.json");
        Files.writeString(existing, "do not overwrite");
        AppState.setEngine(reportingEngine(5, new Robot[0], new Dispatcher()));
        chooseDirectory(exportDir);
        setFileName("report.json");

        invokePrivate("onExport");

        Path collisionOutput = exportDir.resolve("report_1.json");
        assertAll(
                () -> assertEquals("do not overwrite", Files.readString(existing)),
                () -> assertTrue(Files.exists(collisionOutput)),
                () -> assertFalse(Files.exists(exportDir.resolve("report.json.json")))
        );
    }

    @Test
    void export_reports_failure_when_selected_path_is_file_instead_of_directory() throws Exception {
        Path selectedFile = Files.createFile(tempDir.resolve("not-a-directory"));
        chooseDirectory(selectedFile);
        setFileName("results.json");
        AppState.setEngine(reportingEngine(5, new Robot[0], new Dispatcher()));

        invokePrivate("onExport");

        assertAll(
                () -> assertTrue(selectedDirLabel().getText().startsWith("Export failed:")),
                () -> assertTrue(dialogStage.isShowing())
        );
    }

    @Test
    void buildDTO_handles_null_robots_null_dispatcher_unknown_run_and_zero_ticks() throws Exception {
        AppState.clear();
        SimulationEngine engine = reportingEngine(0, null, null);

        ResultsExportDTO dto = (ResultsExportDTO) invokePrivate(
                "buildDTO",
                new Class<?>[]{ SimulationEngine.class },
                engine);

        assertAll(
                () -> assertEquals("unknown", dto.runName),
                () -> assertEquals(0, dto.totalTicks),
                () -> assertEquals(0, dto.completedTasks),
                () -> assertEquals(0, dto.pendingTasks),
                () -> assertEquals(0, dto.totalTasks),
                () -> assertEquals(0.0, dto.throughput, 0.0001),
                () -> assertEquals(0.0, dto.completionRate, 0.0001),
                () -> assertNotNull(dto.robots),
                () -> assertTrue(dto.robots.isEmpty())
        );
    }

    @Test
    void buildDTO_uses_dispatcher_total_tasks_added_when_it_exceeds_visible_work() throws Exception {
        AppState.setConfigPath("finished-run.json");
        SimulationEngine engine = reportingEngine(
                20,
                new Robot[]{
                        new ReportingRobot("R-1", null, 2, 0, 0,
                                0, 0, 100, RobotState.IDLE)
                },
                new ReportingDispatcher(List.of(), 10));

        ResultsExportDTO dto = (ResultsExportDTO) invokePrivate(
                "buildDTO",
                new Class<?>[]{ SimulationEngine.class },
                engine);

        assertAll(
                () -> assertEquals(10, dto.totalTasks),
                () -> assertEquals(2, dto.completedTasks),
                () -> assertEquals(0, dto.pendingTasks),
                () -> assertEquals(20.0, dto.completionRate, 0.0001)
        );
    }

    @Test
    void exporting_existing_recent_directory_moves_it_to_front_without_duplicate() throws Exception {
        Path first = Files.createDirectory(tempDir.resolve("first"));
        Path second = Files.createDirectory(tempDir.resolve("second"));
        writeRecentDirs(first.toString(), second.toString());
        reloadDialog();
        AppState.setEngine(reportingEngine(1, new Robot[0], new Dispatcher()));
        chooseDirectory(second);
        setFileName("results");

        invokePrivate("onExport");

        assertAll(
                () -> assertEquals(second.toString(), prefs().get(PREFS_KEY + 0, null)),
                () -> assertEquals(first.toString(), prefs().get(PREFS_KEY + 1, null))
        );
    }

    @Test
    void cancel_closes_dialog_without_exporting() throws Exception {
        Path exportDir = Files.createDirectory(tempDir.resolve("exports"));
        chooseDirectory(exportDir);
        setFileName("cancelled");

        invokePrivate("onCancel");

        assertAll(
                () -> assertFalse(dialogStage.isShowing()),
                () -> assertFalse(Files.exists(exportDir.resolve("cancelled.json")))
        );
    }

    private void loadDialog() throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/com/openrobotics/fxml/ExportResultsDialog.fxml")));
        Parent root = loader.load();
        controller = loader.getController();
        controller.setDialogStage(dialogStage);
        dialogStage.setScene(new Scene(root, 480, 320));
        dialogStage.show();
    }

    private void reloadDialog() {
        interact(() -> {
            try {
                loadDialog();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void chooseDirectory(Path directory) {
        String path = directory.toString();
        interact(() -> {
            ComboBox<String> combo = directoryCombo();
            if (!combo.getItems().contains(path)) {
                combo.getItems().add(path);
            }
            combo.getSelectionModel().select(path);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void setFileName(String fileName) {
        interact(() -> fileNameField().setText(fileName));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Object invokePrivate(String methodName) throws Exception {
        return invokePrivate(methodName, new Class<?>[0]);
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ExportResultsController.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        Object[] result = new Object[1];
        Throwable[] thrown = new Throwable[1];

        Runnable invocation = () -> {
            try {
                result[0] = method.invoke(controller, args);
            } catch (InvocationTargetException e) {
                thrown[0] = e.getCause();
            } catch (Throwable t) {
                thrown[0] = t;
            }
        };

        if (Platform.isFxApplicationThread()) {
            invocation.run();
        } else {
            interact(invocation);
            WaitForAsyncUtils.waitForFxEvents();
        }

        if (thrown[0] != null) {
            if (thrown[0] instanceof Exception e) {
                throw e;
            }
            if (thrown[0] instanceof Error e) {
                throw e;
            }
            throw new IllegalStateException(thrown[0]);
        }
        return result[0];
    }

    private TextField fileNameField() {
        return lookup("#fileNameField").queryAs(TextField.class);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> directoryCombo() {
        return lookup("#directoryCombo").queryAs(ComboBox.class);
    }

    private Label selectedDirLabel() {
        return lookup("#selectedDirLabel").queryAs(Label.class);
    }

    private void clearPrefs() throws Exception {
        Preferences prefs = prefs();
        for (int i = 0; i < MAX_RECENT; i++) {
            prefs.remove(PREFS_KEY + i);
        }
        prefs.flush();
    }

    private void writeRecentDirs(String... dirs) throws Exception {
        clearPrefs();
        Preferences prefs = prefs();
        for (int i = 0; i < dirs.length; i++) {
            prefs.put(PREFS_KEY + i, dirs[i]);
        }
        prefs.flush();
    }

    private Preferences prefs() {
        return Preferences.userNodeForPackage(ExportResultsController.class);
    }

    private Dispatcher dispatcherWithPendingTasks(int count, int firstId) {
        Dispatcher dispatcher = new Dispatcher();
        for (int i = 0; i < count; i++) {
            dispatcher.addTask(new Task(firstId + i, new Vector2D(i, 0), new Vector2D(i, 1), i + 1));
        }
        return dispatcher;
    }

    private SimulationEngine reportingEngine(int ticks, Robot[] robots, Dispatcher dispatcher) {
        return new ReportingEngine(ticks, robots, dispatcher);
    }

    private static class ReportingEngine extends SimulationEngine {
        private final int ticks;
        private final Robot[] robots;
        private final Dispatcher dispatcher;

        ReportingEngine(int ticks, Robot[] robots, Dispatcher dispatcher) {
            super(new Map(1, 1), new Robot[0], new Dispatcher(), CoordinationPolicy.noOp());
            this.ticks = ticks;
            this.robots = robots;
            this.dispatcher = dispatcher;
        }

        @Override
        public int getTickCounter() {
            return ticks;
        }

        @Override
        public Robot[] getRobots() {
            return robots;
        }

        @Override
        public Dispatcher getDispatcher() {
            return dispatcher;
        }
    }

    private static class ReportingDispatcher extends Dispatcher {
        private final List<Task> tasks;
        private final int totalTasksAdded;

        ReportingDispatcher(List<Task> tasks, int totalTasksAdded) {
            this.tasks = tasks;
            this.totalTasksAdded = totalTasksAdded;
        }

        @Override
        public List<Task> getAllQueuedTasks() {
            return tasks;
        }

        @Override
        public int getTotalTasksAdded() {
            return totalTasksAdded;
        }
    }

    private static class ReportingRobot extends Robot {
        private final NavigationStrategy nav;
        private final int tasksCompleted;
        private final int totalDistanceMoved;
        private final float totalEnergyConsumed;
        private final int totalIdleTicks;
        private final int stuckTicks;
        private final float battery;
        private final RobotState state;

        ReportingRobot(String name, NavigationStrategy nav, int tasksCompleted, int totalDistanceMoved,
                       float totalEnergyConsumed, int totalIdleTicks, int stuckTicks, float battery,
                       RobotState state) {
            super(name, new Vector2D(0, 0));
            this.nav = nav;
            this.tasksCompleted = tasksCompleted;
            this.totalDistanceMoved = totalDistanceMoved;
            this.totalEnergyConsumed = totalEnergyConsumed;
            this.totalIdleTicks = totalIdleTicks;
            this.stuckTicks = stuckTicks;
            this.battery = battery;
            this.state = state;
        }

        @Override
        public NavigationStrategy getNav() {
            return nav;
        }

        @Override
        public int getTasksCompleted() {
            return tasksCompleted;
        }

        @Override
        public int getTotalDistanceMoved() {
            return totalDistanceMoved;
        }

        @Override
        public float getTotalEnergyConsumed() {
            return totalEnergyConsumed;
        }

        @Override
        public int getTotalIdleTicks() {
            return totalIdleTicks;
        }

        @Override
        public int getStuckTicks() {
            return stuckTicks;
        }

        @Override
        public float getBattery() {
            return battery;
        }

        @Override
        public RobotState getState() {
            return state;
        }
    }

    private static class AuditNavigationStrategy implements NavigationStrategy {
        @Override
        public MoveIntention getNextMove(Robot robot, Map map) {
            return null;
        }
    }

    public static class InMemoryPreferencesFactory implements PreferencesFactory {
        private static final MemoryPreferences USER_ROOT = new MemoryPreferences(null, "");
        private static final MemoryPreferences SYSTEM_ROOT = new MemoryPreferences(null, "");

        @Override
        public Preferences userRoot() {
            return USER_ROOT;
        }

        @Override
        public Preferences systemRoot() {
            return SYSTEM_ROOT;
        }
    }

    private static class MemoryPreferences extends AbstractPreferences {
        private final java.util.Map<String, String> values = new ConcurrentHashMap<>();
        private final java.util.Map<String, MemoryPreferences> children = new ConcurrentHashMap<>();

        MemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            values.clear();
            children.clear();
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return children.keySet().toArray(String[]::new);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, childName -> new MemoryPreferences(this, childName));
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
            // In-memory test preferences are always current.
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
            // Nothing to flush for the in-memory implementation.
        }
    }
}
