package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulationEngineReservationKTest {

    @Test
    void reservesFullWindowAndReleasesConsumedStep() {
        Robot robot = movingRobot(
                "00000000-0000-0000-0000-000000000001",
                new Vector2D(0, 0),
                new Vector2D(3, 0),
                1
        );
        SimulationEngine engine = createEngine(5, 3, 3, robot);

        engine.tick();

        assertEquals(new Vector2D(1, 0), robot.getPosition());
        assertNull(engine.getReservationOwner(1, new Vector2D(1, 0)));
        assertEquals(robot.getId(), engine.getReservationOwner(2, new Vector2D(2, 0)));
        assertEquals(robot.getId(), engine.getReservationOwner(3, new Vector2D(3, 0)));
        assertEquals(2, engine.getReservationCountForRobot(robot.getId()));
    }

    @Test
    void lowerUuidWinsWhenWindowsConflict() {
        Robot winner = movingRobot(
                "00000000-0000-0000-0000-000000000001",
                new Vector2D(0, 0),
                new Vector2D(2, 0),
                1
        );
        Robot loser = movingRobot(
                "00000000-0000-0000-0000-000000000002",
                new Vector2D(2, 0),
                new Vector2D(0, 0),
                2
        );
        SimulationEngine engine = createEngine(4, 3, 2, winner, loser);

        engine.tick();

        assertEquals(new Vector2D(1, 0), winner.getPosition());
        assertEquals(new Vector2D(2, 0), loser.getPosition());
        assertEquals(winner.getId(), engine.getReservationOwner(2, new Vector2D(2, 0)));
        assertEquals(1, engine.getReservationCountForRobot(winner.getId()));
        assertEquals(0, engine.getReservationCountForRobot(loser.getId()));
    }

    @Test
    void deniesEntireWindowWhenLaterStepConflicts() {
        Robot firstRobot = movingRobot(
                "00000000-0000-0000-0000-000000000001",
                new Vector2D(0, 0),
                new Vector2D(2, 0),
                1
        );
        Robot secondRobot = movingRobot(
                "00000000-0000-0000-0000-000000000002",
                new Vector2D(2, 2),
                new Vector2D(2, 0),
                2
        );
        SimulationEngine engine = createEngine(5, 4, 2, firstRobot, secondRobot);

        engine.tick();

        assertEquals(new Vector2D(1, 0), firstRobot.getPosition());
        assertEquals(new Vector2D(2, 2), secondRobot.getPosition());
        assertEquals(firstRobot.getId(), engine.getReservationOwner(2, new Vector2D(2, 0)));
        assertNull(engine.getReservationOwner(2, new Vector2D(2, 1)));
        assertEquals(0, engine.getReservationCountForRobot(secondRobot.getId()));
    }

    @Test
    void replanningClearsOldFutureReservations() {
        Robot robot = movingRobot(
                "00000000-0000-0000-0000-000000000001",
                new Vector2D(0, 0),
                new Vector2D(3, 0),
                1
        );
        SimulationEngine engine = createEngine(5, 5, 3, robot);

        engine.tick();
        assertEquals(robot.getId(), engine.getReservationOwner(3, new Vector2D(3, 0)));

        robot.setCurrentTask(inProgressTask(99, new Vector2D(1, 0), new Vector2D(1, 2)));
        robot.setHasPickedUp(true);
        robot.setState(RobotState.MOVING);

        engine.tick();

        assertEquals(new Vector2D(1, 1), robot.getPosition());
        assertNull(engine.getReservationOwner(3, new Vector2D(3, 0)));
        assertEquals(robot.getId(), engine.getReservationOwner(3, new Vector2D(1, 2)));
    }

    @Test
    void cleanupHookReleasesReservationsForAnotherRobot() {
        Robot firstRobot = movingRobot(
                "00000000-0000-0000-0000-000000000001",
                new Vector2D(0, 0),
                new Vector2D(2, 0),
                1
        );
        Robot secondRobot = movingRobot(
                "00000000-0000-0000-0000-000000000002",
                new Vector2D(2, 2),
                new Vector2D(2, 0),
                2
        );
        SimulationEngine engine = createEngine(5, 4, 2, firstRobot, secondRobot);

        engine.tick();
        assertEquals(new Vector2D(2, 2), secondRobot.getPosition());
        engine.clearReservationsForRobot(firstRobot.getId());

        assertEquals(0, engine.getReservationCountForRobot(firstRobot.getId()));

        firstRobot.setCurrentTask(null);
        firstRobot.setHasPickedUp(false);
        firstRobot.setState(RobotState.IDLE);

        engine.tick();

        assertEquals(new Vector2D(2, 1), secondRobot.getPosition());
        assertEquals(secondRobot.getId(), engine.getReservationOwner(3, new Vector2D(2, 0)));
    }

    @Test
    void producesDeterministicOutcomesAcrossEquivalentRuns() {
        SimulationEngine firstRun = createEngine(
                5,
                3,
                3,
                movingRobot("00000000-0000-0000-0000-000000000001", new Vector2D(0, 0), new Vector2D(3, 0), 1),
                movingRobot("00000000-0000-0000-0000-000000000002", new Vector2D(3, 0), new Vector2D(0, 0), 2)
        );
        SimulationEngine secondRun = createEngine(
                5,
                3,
                3,
                movingRobot("00000000-0000-0000-0000-000000000001", new Vector2D(0, 0), new Vector2D(3, 0), 1),
                movingRobot("00000000-0000-0000-0000-000000000002", new Vector2D(3, 0), new Vector2D(0, 0), 2)
        );

        List<String> firstSnapshots = captureSnapshots(firstRun, 3);
        List<String> secondSnapshots = captureSnapshots(secondRun, 3);

        assertEquals(firstSnapshots, secondSnapshots);
    }

    private SimulationEngine createEngine(int width, int height, int k, Robot... robots) {
        Map map = new Map(width, height);
        for (Robot robot : robots) {
            map.addEntity(robot);
        }
        return new SimulationEngine(map, robots, new Dispatcher(), new ReservationKPolicy(k));
    }

    private Robot movingRobot(String uuid, Vector2D start, Vector2D goal, int taskId) {
        Robot robot = new Robot(UUID.fromString(uuid), "Robot-" + taskId, start);
        robot.setCurrentTask(inProgressTask(taskId, start, goal));
        robot.setHasPickedUp(true);
        robot.setState(RobotState.MOVING);
        return robot;
    }

    private Task inProgressTask(int taskId, Vector2D pickup, Vector2D dropoff) {
        Task task = new Task(taskId, pickup, dropoff, 1);
        task.setStatus(TaskStatus.IN_PROGRESS);
        return task;
    }

    private List<String> captureSnapshots(SimulationEngine engine, int ticks) {
        List<String> snapshots = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            engine.tick();
            snapshots.add(snapshot(engine));
        }
        return snapshots;
    }

    private String snapshot(SimulationEngine engine) {
        List<Robot> ordered = new ArrayList<>(List.of(engine.getRobots()));
        ordered.sort(Comparator.comparing(robot -> robot.getId().toString()));

        List<String> parts = new ArrayList<>();
        for (Robot robot : ordered) {
            parts.add(robot.getId() + "@" + robot.getPosition() + "#" + engine.getReservationCountForRobot(robot.getId()));
        }
        return String.join("|", parts);
    }
}
