package com.openrobotics.integration.config;

import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.map.Map;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.ReservationKPolicy;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for config round-trip fidelity with Reservation-K coordination.
 *
 * <p>This test verifies a save → load → save sequence preserves simulation-level settings,
 * coordination policy type/parameter, robot strategy metadata, and basic workload presence when
 * serialized back into DTO form.</p>
 */
public class ConfigRoundTripReservationKIntegrationTest extends SimulationIntegrationTestSupport {

    @TempDir
    Path tempDir;

    /**
     * Verifies Reservation-K configuration survives a double serialization cycle.
     *
     * <p>Scenario:
     * <ol>
     *   <li>Build an in-memory engine with Reservation-K policy, one robot, and one task.</li>
     *   <li>Save config to disk, reload engine from that file, then save again.</li>
     *   <li>Load final JSON into DTO and assert key fields remained stable.</li>
     * </ol>
     *
     * @throws Exception if save/load or reflective policy access fails
     */
    @Test
    void saveLoadSaveRoundTripPreservesReservationKConfiguration() throws Exception {
        Map map = new Map(5, 3);

        Robot robot = new Robot(
                UUID.fromString("00000000-0000-0000-0000-000000000021"),
                "GreedyBot",
                pos(1, 1)
        );
        robot.setNav(new GreedyNavigationStrategy(42L));
        robot.setSensor(new ProximitySensor());
        robot.setBattery(73.0f);
        robot.setState(RobotState.IDLE);
        robot.setStuckTicks(4);
        map.addEntity(robot);
        map.addEntity(new ChargingStation("Charge-1", pos(0, 0)));

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(new Task(1, pos(1, 1), pos(4, 1), 2));

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robot},
                dispatcher,
                new ReservationKPolicy(3),
                "reservation_roundtrip",
                50,
                250,
                77L
        );

        Path firstPath = tempDir.resolve("reservation-roundtrip-1.json");
        Path secondPath = tempDir.resolve("reservation-roundtrip-2.json");

        engine.configSaving(firstPath.toString());
        SimulationEngine loadedEngine = new SimulationEngine(firstPath.toString());
        loadedEngine.configSaving(secondPath.toString());

        SimulationConfigDTO dto = ConfigLoader.load(secondPath.toString(), SimulationConfigDTO.class);

        assertEquals("reservation_roundtrip", dto.config.runName);
        assertEquals(50, dto.config.tickMs);
        assertEquals(250, dto.config.maxTicks);
        assertEquals(77L, dto.config.seed);

        assertNotNull(dto.coordination);
        assertEquals("RESERVATION_K", dto.coordination.type);
        assertEquals(3, dto.coordination.k);

        assertEquals(1, dto.entities.robots.size());
        assertEquals("GREEDY", dto.entities.robots.get(0).navigationStrategy);
        assertEquals("PROXIMITY", dto.entities.robots.get(0).sensorStrategy);
        assertEquals(73.0f, dto.entities.robots.get(0).battery, 0.001f);
        assertEquals("IDLE", dto.entities.robots.get(0).state);
        assertEquals(4, dto.entities.robots.get(0).stuckTicks);

        assertEquals(1, loadedEngine.getRobots().length);
        assertInstanceOf(ReservationKPolicy.class, getCoordinationPolicy(loadedEngine));
        assertInstanceOf(GreedyNavigationStrategy.class, loadedEngine.getRobots()[0].getNav());
        assertInstanceOf(ProximitySensor.class, loadedEngine.getRobots()[0].getSensor());
        assertEquals(1, loadedEngine.getDispatcher().getPendingTaskCount());
    }

    /**
     * Reads the engine's private coordination policy field for runtime type assertions.
     *
     * @param engine loaded simulation engine instance
     * @return current coordination policy object
     * @throws ReflectiveOperationException if reflective lookup/access fails
     */
    private Object getCoordinationPolicy(SimulationEngine engine) throws ReflectiveOperationException {
        Field field = SimulationEngine.class.getDeclaredField("coordinationPolicy");
        field.setAccessible(true);
        return field.get(engine);
    }
}
