package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SimulationEngine#isFinished()} completion semantics.
 *
 * <p>This suite verifies tick-gating behavior, queued-task influence, and robot-state influence on
 * the engine's finished predicate.</p>
 */
public class SimulationEngineFinishedTest {

    /**
     * Sets the private tick counter via reflection to create deterministic completion states.
     *
     * @param engine engine instance under test
     * @param tick tick value to inject
     * @throws Exception if reflective field access fails
     */
    private static void setTick(SimulationEngine engine, int tick) throws Exception {
        Field f = SimulationEngine.class.getDeclaredField("tickCounter");
        f.setAccessible(true);
        f.setInt(engine, tick);
    }

    /**
     * Creates a minimal engine fixture with a fresh map and no-op coordination policy.
     */
    private static SimulationEngine newEngine(Robot[] robots, Dispatcher dispatcher) {
        Map map = new Map(5, 5);
        return new SimulationEngine(map, robots, dispatcher, CoordinationPolicy.noOp());
    }

    /**
     * Verifies a newly created engine at tick zero is never considered finished.
     */
    @Test
    public void notFinishedAtTickZero() {
        SimulationEngine engine = newEngine(new Robot[0], new Dispatcher());
        assertFalse(engine.isFinished(), "fresh engine at tick 0 must not be finished");
    }

    /**
     * Verifies queued tasks keep the engine unfinished even after tick advancement.
     *
     * @throws Exception if reflective tick injection fails
     */
    @Test
    public void notFinishedWhenTasksStillQueued() throws Exception {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(new Task(1L, new Vector2D(0, 0), new Vector2D(1, 1), 1));
        SimulationEngine engine = newEngine(new Robot[0], dispatcher);
        setTick(engine, 5);
        assertFalse(engine.isFinished());
    }

    /**
     * Verifies engine is finished when ticks advanced, no tasks remain, and all robots are idle.
     *
     * @throws Exception if reflective tick injection fails
     */
    @Test
    public void finishedWhenTickAdvancedAllIdleNoTasks() throws Exception {
        Robot r = new Robot("r1", new Vector2D(0, 0));
        r.setState(RobotState.IDLE);
        SimulationEngine engine = newEngine(new Robot[]{r}, new Dispatcher());
        setTick(engine, 3);
        assertTrue(engine.isFinished());
    }

    /**
     * Verifies any non-idle robot keeps the engine unfinished despite tick advancement.
     *
     * @throws Exception if reflective tick injection fails
     */
    @Test
    public void notFinishedWhenARobotIsNonIdle() throws Exception {
        Robot r = new Robot("r1", new Vector2D(0, 0));
        r.setState(RobotState.MOVING);
        SimulationEngine engine = newEngine(new Robot[]{r}, new Dispatcher());
        setTick(engine, 3);
        assertFalse(engine.isFinished());
    }
}
