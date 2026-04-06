package com.openrobotics.controllers;

import com.openrobotics.AppState;
import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SetupControllerTest extends ApplicationTest {

    private SetupController controller;

    @Override
    public void start(Stage stage) throws Exception {
        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
        ScreenNavigator.setPrimaryStage(stage);
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/openrobotics/fxml/SetupScreen.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root, 1200, 700));
        stage.show();
    }

    @Test
    void initialize_sets_expected_defaults() {
        ComboBox<?> mapCombo = lookup("#mapCombo").queryAs(ComboBox.class);
        Spinner<?> robotCountSpinner = lookup("#robotCountSpinner").queryAs(Spinner.class);
        ComboBox<?> navAlgoCombo = lookup("#navAlgoCombo").queryAs(ComboBox.class);
        ComboBox<?> policyCombo = lookup("#policyCombo").queryAs(ComboBox.class);
        Spinner<?> reservationKSpinner = lookup("#reservationKSpinner").queryAs(Spinner.class);
        TextField maxTicksField = lookup("#maxTicksField").queryAs(TextField.class);
        TextField tickMsField = lookup("#tickMsField").queryAs(TextField.class);
        TextField runNameField = lookup("#runNameField").queryAs(TextField.class);

        assertEquals("baseline_small", mapCombo.getValue());
        assertEquals(4, robotCountSpinner.getValue());
        assertEquals("GREEDY", navAlgoCombo.getValue());
        assertEquals("NONE", policyCombo.getValue());
        assertTrue(reservationKSpinner.isDisabled());
        assertEquals("30000", maxTicksField.getText());
        assertEquals("50", tickMsField.getText());
        assertEquals("experiment_1", runNameField.getText());
    }

    @Test
    void reservation_spinner_enables_only_for_reservation_k_policy() {
        ComboBox<String> policyCombo = lookup("#policyCombo").queryAs(ComboBox.class);
        Spinner<?> reservationKSpinner = lookup("#reservationKSpinner").queryAs(Spinner.class);

        interact(() -> policyCombo.setValue("RESERVATION_K"));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(reservationKSpinner.isDisabled());

        interact(() -> policyCombo.setValue("NONE"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(reservationKSpinner.isDisabled());
    }

    @Test
    void invalid_max_ticks_shows_validation_error() {
        TextField maxTicksField = lookup("#maxTicksField").queryAs(TextField.class);
        Label statusLabel = lookup("#statusLabel").queryAs(Label.class);
        Button startButton = lookup((Button b) -> "START SIMULATION".equals(b.getText())).queryAs(Button.class);

        interact(() -> maxTicksField.setText("0"));
        clickOn(startButton);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("⚠ Max ticks must be a positive integer.", statusLabel.getText());
    }

    @Test
    void invalid_tick_ms_shows_validation_error() {
        TextField maxTicksField = lookup("#maxTicksField").queryAs(TextField.class);
        TextField tickMsField = lookup("#tickMsField").queryAs(TextField.class);
        Label statusLabel = lookup("#statusLabel").queryAs(Label.class);
        Button startButton = lookup((Button b) -> "START SIMULATION".equals(b.getText())).queryAs(Button.class);

        interact(() -> {
            maxTicksField.setText("100");
            tickMsField.setText("-1");
        });
        clickOn(startButton);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("⚠ Tick ms must be a positive integer.", statusLabel.getText());
    }

    @Test
    void reset_buttons_restore_defaults() {
        ComboBox<String> policyCombo = lookup("#policyCombo").queryAs(ComboBox.class);
        TextField runNameField = lookup("#runNameField").queryAs(TextField.class);

        interact(() -> {
            policyCombo.setValue("RESERVATION_K");
            runNameField.setText("custom_run");
        });
        invokePrivate(controller, "onResetPolicy");
        invokePrivate(controller, "onResetRunName");
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("NONE", policyCombo.getValue());
        assertEquals("experiment_1", runNameField.getText());
    }

    private void invokePrivate(Object target, String methodName) {
        interact(() -> {
            try {
                Method method = target.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(target);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
