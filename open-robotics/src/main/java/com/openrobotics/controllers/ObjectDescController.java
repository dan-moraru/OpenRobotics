package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** controller for ObjectDescDialog.fxml; shows a description of a placeable object and lets the user add it to the viewport (§4.2) */
public class ObjectDescController implements ScreenNavigator.DialogController {

    @FXML private Label objectIconLabel;
    @FXML private Label objectNameLabel;
    @FXML private Label objectTypeLabel;
    @FXML private Label objectDescLabel;
    @FXML private VBox  propsOverview;

    private Stage  dialogStage;
    private String objectType;
    private boolean addRequested = false;

    @Override
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /** populates all labels for the given type. @param type one of: ROBOT, CHARGER, STATION, DOCK, WALL, INTERSECTION */
    public void setObjectType(String type) {
        this.objectType = type;
        if (propsOverview != null) {
            propsOverview.getChildren().clear();
        }
        switch (type) {
            case "ROBOT" -> {
                objectIconLabel.setText("🤖");
                objectNameLabel.setText("Robot");
                objectTypeLabel.setText("Simulation Actor");
                objectDescLabel.setText(
                        "A humanoid warehouse robot that navigates the grid and completes " +
                        "pick-and-deliver tasks. Behaviour is governed by the chosen " +
                        "navigation algorithm and coordination policy.");
                addPropRow("Navigation", "Greedy / BUG / RTA*");
                addPropRow("Battery",    "100 units (configurable)");
                addPropRow("Speed",      "1 tile / tick");
            }
            case "CHARGER" -> {
                objectIconLabel.setText("⚡");
                objectNameLabel.setText("Charging Station");
                objectTypeLabel.setText("Infrastructure – Power");
                objectDescLabel.setText(
                        "Robots travel to a charging station when their battery falls below " +
                        "the low-battery threshold. Multiple robots may queue.");
                addPropRow("Charge rate", "5 units / tick");
                addPropRow("Capacity",    "4 robots (queue)");
            }
            case "STATION" -> {
                objectIconLabel.setText("📦");
                objectNameLabel.setText("Pick / Deposit Station");
                objectTypeLabel.setText("Infrastructure – Task Origin");
                objectDescLabel.setText(
                        "Source or destination of warehouse tasks. Robots pick up or deposit " +
                        "items here as part of pick-and-deliver assignments.");
                addPropRow("Task type", "PICK or DEPOSIT");
            }
            case "DOCK" -> {
                objectIconLabel.setText("🚪");
                objectNameLabel.setText("Loading Dock");
                objectTypeLabel.setText("Infrastructure – Entry / Exit");
                objectDescLabel.setText(
                        "Entry and exit point for external deliveries. Robots may be assigned " +
                        "tasks that originate or terminate at a dock.");
            }
            case "WALL" -> {
                objectIconLabel.setText("🧱");
                objectNameLabel.setText("Wall / Obstacle");
                objectTypeLabel.setText("Environment – Static Obstacle");
                objectDescLabel.setText(
                        "A static obstacle that blocks robot movement. Robots must navigate " +
                        "around walls. Used to define aisle structure.");
            }
            case "INTERSECTION" -> {
                objectIconLabel.setText("╋");
                objectNameLabel.setText("Traffic Intersection");
                objectTypeLabel.setText("Traffic Control – Intersection Marker");
                objectDescLabel.setText(
                        "Marks a traversable floor tile as a traffic-rules intersection. " +
                        "Robots must coordinate entry through this tile when Traffic Rules " +
                        "coordination is active.");
                addPropRow("Placement", "Traversable floor tiles only");
                addPropRow("Edit mode", "Drop again to remove");
            }
            default -> {
                objectIconLabel.setText("?");
                objectNameLabel.setText(type);
                objectTypeLabel.setText("");
                objectDescLabel.setText("No description available.");
            }
        }
    }

    @FXML
    private void onAdd() {
        addRequested = true;
        close();
    }

    @FXML
    private void onClose() {
        close();
    }

    /** returns true if the user pressed "Add to Viewport" */
    public boolean isAddRequested() { return addRequested; }
    public String  getObjectType()  { return objectType; }

    private void close() {
        if (dialogStage != null) dialogStage.close();
    }

    private void addPropRow(String key, String value) {
        if (propsOverview == null || propsOverview.getChildren() == null) {
            return;
        }
        Label row = new Label(key + ":  " + value);
        row.setStyle("-fx-text-fill: #404040; -fx-font-size: 12px;");
        propsOverview.getChildren().add(row);
    }
}
