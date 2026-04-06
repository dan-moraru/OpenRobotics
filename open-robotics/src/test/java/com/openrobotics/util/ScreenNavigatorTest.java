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

public class ScreenNavigatorTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        ScreenNavigator.setPrimaryStage(stage);
        stage.setScene(new Scene(new StackPane(), 300, 200));
        stage.show();
    }

    @Test
    void loadScreen_throws_wrapped_exception_when_primary_stage_is_missing() {
        interact(() -> ScreenNavigator.setPrimaryStage(null));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> interact(ScreenNavigator::goToWelcome));

        // TestFX runs callables on the FX thread; failures may be wrapped (e.g. ExecutionException).
        assertNotNull(findCauseOfType(ex, IllegalStateException.class));
    }

    @Test
    void loadScreen_throws_wrapped_exception_for_unknown_fxml_path() {
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> interact(() -> ScreenNavigator.loadScreen("/com/openrobotics/fxml/DoesNotExist.fxml"))
        );

        assertNotNull(findCauseOfType(ex, IllegalArgumentException.class));
    }

    // Follows {@code getCause()} — TestFX {@code interact} may wrap FX-thread failures.
    private static <T extends Throwable> T findCauseOfType(Throwable ex, Class<T> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    @Test
    void openDialog_throws_for_unknown_fxml_path() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ScreenNavigator.openDialog("/com/openrobotics/fxml/DoesNotExistDialog.fxml")
        );

        assertTrue(ex.getMessage().contains("FXML resource not found"));
    }

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
