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

/**
 * JavaFX controller tests for {@link ObjectDescController}.
 *
 * <p>This suite validates that the controller maps known object types to the expected
 * UI copy (title, subtype, descriptive text, and properties list), falls back safely for unknown
 * object types, and correctly drives dialog outcomes for add/close actions.
 *
 * <p>The controller under test is instantiated directly and its FXML-injected fields are provided
 * via reflection to keep tests focused on controller behavior rather than FXML loading concerns.
 */
public class ObjectDescControllerTest extends ApplicationTest {

    private ObjectDescController controller;
    private Stage dialogStage;
    private Label objectNameLabel;
    private Label objectTypeLabel;
    private Label objectDescLabel;
    private VBox propsOverview;

    /**
     * Initializes a minimal JavaFX stage and wires a fresh controller instance.
     *
     * <p>This method simulates FXML injection by placing test labels/containers into the
     * controller's private fields, assigns the dialog stage, and shows the stage so dialog-closing
     * behavior can be asserted in action tests.
     *
     * @param stage the JavaFX stage provided by TestFX
     */
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

    /**
     * Verifies that selecting {@code ROBOT} populates all robot-specific UI content.
     *
     * <p>Asserts title/subtype labels, descriptive body text, and that the expected
     * number of robot property rows are rendered.
     */
    @Test
    void set_object_type_populates_robot_content() {
        interact(() -> controller.setObjectType("ROBOT"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Robot", objectNameLabel.getText());
        assertEquals("Simulation Actor", objectTypeLabel.getText());
        assertTrue(objectDescLabel.getText().contains("warehouse robot"));
        assertEquals(3, propsOverview.getChildren().size());
    }

    /**
     * Verifies unknown object-type handling uses the generic fallback presentation.
     *
     * <p>The controller should preserve the raw object type value while clearing subtype text,
     * showing a default "no description" message, and leaving the properties container empty.
     */
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

    /**
     * Verifies that selecting {@code INTERSECTION} populates intersection-specific UI content.
     *
     * <p>Asserts expected labels and description fragment, and validates that the properties
     * overview contains exactly the rows defined for this object type.
     */
    @Test
    void set_object_type_populates_intersection_content() {
        interact(() -> controller.setObjectType("INTERSECTION"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Traffic Intersection", objectNameLabel.getText());
        assertEquals("Traffic Control – Intersection Marker", objectTypeLabel.getText());
        assertTrue(objectDescLabel.getText().contains("traversable floor tile"));
        assertEquals(2, propsOverview.getChildren().size());
    }

    /**
     * Verifies that invoking the add action sets the add-requested flag and closes the dialog.
     *
     * <p>This confirms state mutation ({@link ObjectDescController#isAddRequested()}) and
     * user-visible stage behavior occur together when the add handler executes.
     */
    @Test
    void add_sets_flag_and_closes_dialog() {
        interact(() -> controller.setObjectType("WALL"));
        invokePrivate("onAdd");
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(controller.isAddRequested());
        assertFalse(dialogStage.isShowing());
    }

    /**
     * Verifies that invoking the close action does not request add and closes the dialog.
     *
     * <p>This guards against accidental add-state mutation when a user dismisses
     * the dialog without confirming.
     */
    @Test
    void close_leaves_add_flag_false() {
        interact(() -> controller.setObjectType("WALL"));
        invokePrivate("onClose");
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(controller.isAddRequested());
        assertFalse(dialogStage.isShowing());
    }

    /**
     * Injects a value into a private controller field to emulate FXML wiring.
     *
     * @param fieldName private field name declared in {@link ObjectDescController}
     * @param value object instance to assign to that field
     * @throws RuntimeException if reflection access or assignment fails
     */
    private void inject(String fieldName, Object value) {
        try {
            Field field = ObjectDescController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(controller, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invokes a no-arg private controller method on the JavaFX thread.
     *
     * <p>This is used for action handlers (for example, {@code onAdd} and {@code onClose})
     * that are intentionally non-public but still need direct behavioral verification.
     *
     * @param methodName private method name to invoke
     * @throws RuntimeException if reflection lookup/invocation fails
     */
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
