package com.openrobotics.controllers;

import com.openrobotics.util.ScreenNavigator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JavaFX navigation test for {@link WelcomeController}.
 *
 * <p>This test suite verifies that the welcome screen loads correctly and that the primary
 * call-to-action button routes users to the setup screen.</p>
 */
public class WelcomeControllerTest extends ApplicationTest {

    /**
     * Loads the welcome screen FXML on the TestFX stage.
     *
     * @param stage JavaFX stage supplied by TestFX
     * @throws Exception if FXML loading fails
     */
    @Override
    public void start(Stage stage) throws Exception {
        ScreenNavigator.setPrimaryStage(stage);
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/openrobotics/fxml/WelcomeScreen.fxml"));
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1200, 700));
        stage.show();
    }

    /**
     * Verifies clicking the "START SETUP" action navigates to the setup screen.
     *
     * <p>Navigation success is asserted by querying a setup-specific control ({@code #mapCombo}).</p>
     */
    @Test
    void start_setup_navigates_to_setup_screen() {
        Button startButton = lookup((Button b) ->
                b.getText() != null && b.getText().contains("START SETUP")).queryAs(Button.class);
        clickOn(startButton);
        WaitForAsyncUtils.waitForFxEvents();

        ComboBox<?> mapCombo = lookup("#mapCombo").queryAs(ComboBox.class);
        assertNotNull(mapCombo);
    }
}
