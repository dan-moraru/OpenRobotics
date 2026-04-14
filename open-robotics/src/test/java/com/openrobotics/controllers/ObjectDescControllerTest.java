package com.openrobotics.controllers;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectDescControllerTest extends ApplicationTest {

    private ObjectDescController controller;
    private Stage dialogStage;
    private Label objectNameLabel;
    private Label objectTypeLabel;
    private Label objectDescLabel;
    private VBox propsOverview;

    @Override
    public void start(Stage stage) {
        dialogStage = stage;
        controller = new ObjectDescController();
        objectNameLabel = new Label();
        objectTypeLabel = new Label();
        objectDescLabel = new Label();
        propsOverview = new VBox();

        inject("objectIconLabel", new Label());
        inject("objectNameLabel", objectNameLabel);
        inject("objectTypeLabel", objectTypeLabel);
        inject("objectDescLabel", objectDescLabel);
        inject("propsOverview", propsOverview);

        controller.setDialogStage(stage);
        stage.setScene(new Scene(new VBox(), 300, 200));
        stage.show();
    }

    @Test
    void set_object_type_populates_robot_content() {
        interact(() -> controller.setObjectType("ROBOT"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Robot", objectNameLabel.getText());
        assertEquals("Simulation Actor", objectTypeLabel.getText());
        assertTrue(objectDescLabel.getText().contains("warehouse robot"));
        assertEquals(3, propsOverview.getChildren().size());
    }

    @Test
    void unknown_object_type_falls_back_to_generic_description() {
        interact(() -> controller.setObjectType("MYSTERY"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("MYSTERY", controller.getObjectType());
        assertEquals("MYSTERY", objectNameLabel.getText());
        assertEquals("", objectTypeLabel.getText());
        assertEquals("No description available.", objectDescLabel.getText());
        assertTrue(propsOverview.getChildren().isEmpty());
    }

    @Test
    void set_object_type_populates_intersection_content() {
        interact(() -> controller.setObjectType("INTERSECTION"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Traffic Intersection", objectNameLabel.getText());
        assertEquals("Traffic Control – Intersection Marker", objectTypeLabel.getText());
        assertTrue(objectDescLabel.getText().contains("traversable floor tile"));
        assertEquals(2, propsOverview.getChildren().size());
    }

    @Test
    void add_sets_flag_and_closes_dialog() {
        interact(() -> controller.setObjectType("WALL"));
        invokePrivate("onAdd");
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(controller.isAddRequested());
        assertFalse(dialogStage.isShowing());
    }

    @Test
    void close_leaves_add_flag_false() {
        interact(() -> controller.setObjectType("WALL"));
        invokePrivate("onClose");
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(controller.isAddRequested());
        assertFalse(dialogStage.isShowing());
    }

    private void inject(String fieldName, Object value) {
        try {
            Field field = ObjectDescController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(controller, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void invokePrivate(String methodName) {
        interact(() -> {
            try {
                Method method = ObjectDescController.class.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
