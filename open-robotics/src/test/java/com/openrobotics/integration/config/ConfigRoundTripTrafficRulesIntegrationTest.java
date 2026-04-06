package com.openrobotics.integration.config;

import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.robot.navigation.BugNavigationStrategy;
import com.openrobotics.robot.navigation.RtaStarNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.robot.sensors.RangeSensor;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.simulationcore.TrafficRulesPolicy;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigRoundTripTrafficRulesIntegrationTest extends SimulationIntegrationTestSupport {

    @TempDir
    Path tempDir;

    @Test
    void saveLoadSaveRoundTripPreservesEntitiesMetadataAndTrafficRules() throws Exception {
        Map map = new Map(6, 4);
        map.getTile(5, 3).setOccupied(true);

        Robot bugBot = new Robot(
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                "BugBot",
                pos(1, 1)
        );
        bugBot.setNav(new BugNavigationStrategy(17L));
        bugBot.setSensor(new RangeSensor());
        bugBot.setBattery(87.0f);
        bugBot.setState(RobotState.MOVING);
        bugBot.setStuckTicks(2);

        Robot starBot = new Robot(
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                "StarBot",
                pos(2, 2)
        );
        starBot.setNav(new RtaStarNavigationStrategy(17L));
        starBot.setSensor(new ProximitySensor());
        starBot.setBattery(65.0f);
        starBot.setState(RobotState.IDLE);
        starBot.setStuckTicks(1);

        map.addEntity(bugBot);
        map.addEntity(starBot);
        map.addEntity(new ChargingStation("Charge-1", pos(0, 0)));
        map.addEntity(new DeliveryStation("Deliver-1", pos(4, 3)));
        map.addEntity(new Rack("Rack-1", pos(3, 1)));
        map.addEntity(new Obstacle("Wall-1", pos(5, 0)));

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.addTask(new Task(101, pos(1, 0), pos(4, 0), 7));
        dispatcher.addTask(new Task(102, pos(0, 3), pos(5, 3), 3));

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{bugBot, starBot},
                dispatcher,
                new TrafficRulesPolicy(Set.of(map.getTile(2, 1), map.getTile(2, 2))),
                "traffic_roundtrip",
                75,
                999,
                12345L
        );

        Path firstPath = tempDir.resolve("traffic-roundtrip-1.json");
        Path secondPath = tempDir.resolve("traffic-roundtrip-2.json");

        engine.configSaving(firstPath.toString());
        SimulationEngine loadedEngine = new SimulationEngine(firstPath.toString());
        loadedEngine.configSaving(secondPath.toString());

        SimulationConfigDTO dto = ConfigLoader.load(secondPath.toString(), SimulationConfigDTO.class);

        assertEquals("traffic_roundtrip", dto.config.runName);
        assertEquals(75, dto.config.tickMs);
        assertEquals(999, dto.config.maxTicks);
        assertEquals(12345L, dto.config.seed);

        assertEquals(6, dto.map.width);
        assertEquals(4, dto.map.height);
        assertTrue(hasOccupiedTile(dto.map.tiles, 5, 3));

        assertEquals(2, dto.entities.robots.size());
        assertEquals(2, dto.entities.stations.size());
        assertEquals(1, dto.entities.racks.size());
        assertEquals(1, dto.entities.obstacles.size());
        assertEquals(2, dto.tasks.size());

        SimulationConfigDTO.RobotDTO bugBotDto = findRobot(dto.entities.robots, "BugBot");
        assertEquals("BUG", bugBotDto.navigationStrategy);
        assertEquals("RANGE", bugBotDto.sensorStrategy);
        assertEquals("MOVING", bugBotDto.state);
        assertEquals(2, bugBotDto.stuckTicks);
        assertEquals(87.0f, bugBotDto.battery, 0.001f);

        SimulationConfigDTO.RobotDTO starBotDto = findRobot(dto.entities.robots, "StarBot");
        assertEquals("RTA_STAR", starBotDto.navigationStrategy);
        assertEquals("PROXIMITY", starBotDto.sensorStrategy);
        assertEquals("IDLE", starBotDto.state);

        assertNotNull(dto.coordination);
        assertEquals("TRAFFIC_RULES", dto.coordination.type);
        assertEquals(2, dto.coordination.intersections.size());

        assertEquals(2, loadedEngine.getRobots().length);
        Robot loadedBugBot = findRobot(loadedEngine.getRobots(), "BugBot");
        Robot loadedStarBot = findRobot(loadedEngine.getRobots(), "StarBot");
        assertInstanceOf(BugNavigationStrategy.class, loadedBugBot.getNav());
        assertInstanceOf(RangeSensor.class, loadedBugBot.getSensor());
        assertInstanceOf(RtaStarNavigationStrategy.class, loadedStarBot.getNav());
        assertInstanceOf(ProximitySensor.class, loadedStarBot.getSensor());

        assertEquals(6, loadedEngine.getMap().getEntities().size());
        assertEquals(2, loadedEngine.getDispatcher().getPendingTaskCount());
        assertEquals(2, countEntitiesOfType(loadedEngine.getMap().getEntities(), com.openrobotics.map.entities.station.Station.class));
        assertEquals(1, countEntitiesOfType(loadedEngine.getMap().getEntities(), Rack.class));
        assertEquals(1, countEntitiesOfType(loadedEngine.getMap().getEntities(), Obstacle.class));
    }

    private boolean hasOccupiedTile(List<SimulationConfigDTO.TileDTO> tiles, int x, int y) {
        if (tiles == null) {
            return false;
        }
        return tiles.stream().anyMatch(tile -> tile.x == x && tile.y == y && tile.isOccupied);
    }

    private SimulationConfigDTO.RobotDTO findRobot(List<SimulationConfigDTO.RobotDTO> robots, String name) {
        return robots.stream()
                .filter(robot -> name.equals(robot.name))
                .findFirst()
                .orElseThrow();
    }

    private Robot findRobot(Robot[] robots, String name) {
        for (Robot robot : robots) {
            if (name.equals(robot.getName())) {
                return robot;
            }
        }
        throw new AssertionError("Robot not found: " + name);
    }

    private int countEntitiesOfType(List<MapEntity> entities, Class<?> type) {
        return (int) entities.stream().filter(type::isInstance).count();
    }
}
