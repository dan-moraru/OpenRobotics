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

/** Tests for {@link SimulationEngine#isFinished()}. */
public class SimulationEngineFinishedTest {

    private static void setTick(SimulationEngine engine, int tick) throws Exception {
        Field f = SimulationEngine.class.getDeclaredField("tickCounter");
        f.setAccessible(true);
        f.setInt(engine, tick);
    }

    private static SimulationEngine newEngine(Robot[] robots, Dispatcher dispatcher) {
        Map map = new Map(5, 5);
        return new SimulationEngine(map, robots, dispatcher, CoordinationPolicy.noOp());
    }

    @Test
    public void notFinishedAtTickZero() {
        SimulationEngine engine = newEngine(new Robot[0], new Dispatcher());
        assertFalse(engine.isFinished(), "fresh engine at tick 0 must not be finished");
    }

    @Test
    public void notFinishedWhenTasksStillQueued() throws Exception {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(new Task(1L, new Vector2D(0, 0), new Vector2D(1, 1), 1));
        SimulationEngine engine = newEngine(new Robot[0], dispatcher);
        setTick(engine, 5);
        assertFalse(engine.isFinished());
    }

    @Test
    public void finishedWhenTickAdvancedAllIdleNoTasks() throws Exception {
        Robot r = new Robot("r1", new Vector2D(0, 0));
        r.setState(RobotState.IDLE);
        SimulationEngine engine = newEngine(new Robot[]{r}, new Dispatcher());
        setTick(engine, 3);
        assertTrue(engine.isFinished());
    }

    @Test
    public void notFinishedWhenARobotIsNonIdle() throws Exception {
        Robot r = new Robot("r1", new Vector2D(0, 0));
        r.setState(RobotState.MOVING);
        SimulationEngine engine = newEngine(new Robot[]{r}, new Dispatcher());
        setTick(engine, 3);
        assertFalse(engine.isFinished());
    }
}
