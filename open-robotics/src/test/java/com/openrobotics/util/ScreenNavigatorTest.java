package com.openrobotics.util;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JavaFX tests for {@link ScreenNavigator} screen/dialog loading behavior.
 *
 * <p>This suite verifies expected failure modes for missing stage/FXML resources and confirms
 * successful root replacement during normal screen navigation.</p>
 */
public class ScreenNavigatorTest extends ApplicationTest {

    /**
     * Initializes the primary stage used by {@link ScreenNavigator} for each TestFX run.
     *
     * @param stage JavaFX stage provided by TestFX
     */
    @Override
    public void start(Stage stage) {
        ScreenNavigator.setPrimaryStage(stage);
        stage.setScene(new Scene(new StackPane(), 300, 200));
        stage.show();
    }

    /**
     * Verifies screen loading fails (wrapped by TestFX) when primary stage is unset.
     */
    @Test
    void loadScreen_throws_wrapped_exception_when_primary_stage_is_missing() {
        interact(() -> ScreenNavigator.setPrimaryStage(null));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> interact(ScreenNavigator::goToWelcome));

        assertNotNull(findCauseOfType(ex, IllegalStateException.class));
    }

    /**
     * Verifies loading an unknown FXML path throws a wrapped argument/resource error.
     */
    @Test
    void loadScreen_throws_wrapped_exception_for_unknown_fxml_path() {
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> interact(() -> ScreenNavigator.loadScreen("/com/openrobotics/fxml/DoesNotExist.fxml"))
        );

        assertNotNull(findCauseOfType(ex, IllegalArgumentException.class));
    }

    /**
     * Walks the exception cause chain and returns the first cause assignable to {@code type}.
     *
     * <p>Useful because TestFX {@code interact} often wraps FX-thread exceptions.</p>
     */
    private static <T extends Throwable> T findCauseOfType(Throwable ex, Class<T> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    /**
     * Verifies dialog loading reports a clear error when FXML resource path is invalid.
     */
    @Test
    void openDialog_throws_for_unknown_fxml_path() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScreenNavigator.openDialog("/com/openrobotics/fxml/DoesNotExistDialog.fxml")
        );

        assertTrue(ex.getMessage().contains("FXML resource not found"));
    }

    /**
     * Verifies navigation calls swap the scene root to the requested destination screen.
     */
    @Test
    void loadScreen_replaces_scene_root_with_requested_screen() {
        interact(ScreenNavigator::goToWelcome);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(lookup("#rootPane").tryQuery().isPresent());

        interact(ScreenNavigator::goToSetup);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(lookup("#mapCombo").tryQuery().isPresent());
    }
}
