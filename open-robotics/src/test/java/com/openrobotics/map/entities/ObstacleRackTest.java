package com.openrobotics.map.entities;

import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Obstacle} and {@link Rack}.
 *
 * <p>Both classes extend {@link MapEntity} without adding new public state,
 * so these tests focus on correct construction, inheritance, and type
 * identity (used by {@code Map.isTraversable}).
 */
public class ObstacleRackTest {

    /**
     * Obstacle stores name and position from the two-argument constructor.
     */
    @Test
    public void testObstacleNameAndPosition() {
        Obstacle o = new Obstacle("Wall-1", new Vector2D(4, 2));
        assertEquals("Wall-1", o.getName());
        assertEquals(new Vector2D(4, 2), o.getPosition());
    }

    /**
     * Obstacle auto-generates a non-null UUID.
     */
    @Test
    public void testObstacleAutoUuid() {
        Obstacle o = new Obstacle("Wall-2", new Vector2D(0, 0));
        assertNotNull(o.getId());
    }

    /**
     * Obstacle explicit-UUID constructor stores the provided UUID.
     */
    @Test
    public void testObstacleExplicitUuid() {
        UUID id = UUID.randomUUID();
        Obstacle o = new Obstacle(id, "Wall-3", new Vector2D(1, 1));
        assertEquals(id, o.getId());
    }

    /**
     * Obstacle explicit-UUID constructor also preserves name and position.
     */
    @Test
    public void testObstacleExplicitUuidStoresNameAndPosition() {
        UUID id = UUID.randomUUID();
        Obstacle o = new Obstacle(id, "Wall-3", new Vector2D(1, 1));
        assertEquals("Wall-3", o.getName());
        assertEquals(new Vector2D(1, 1), o.getPosition());
    }

    /**
     * Obstacle is a subtype of MapEntity (required by Map.isTraversable instanceof check).
     */
    @Test
    public void testObstacleIsMapEntity() {
        Obstacle o = new Obstacle("Wall", new Vector2D(0, 0));
        assertInstanceOf(MapEntity.class, o);
    }

    /**
     * Two Obstacle instances have distinct auto-generated UUIDs.
     */
    @Test
    public void testObstacleUniqueIds() {
        Obstacle a = new Obstacle("A", new Vector2D(0, 0));
        Obstacle b = new Obstacle("B", new Vector2D(0, 0));
        assertNotEquals(a.getId(), b.getId());
    }

    /**
     * Obstacle inherits mutable name and position setters from MapEntity.
     */
    @Test
    public void testObstacleInheritedSetters() {
        Obstacle o = new Obstacle("Wall", new Vector2D(0, 0));

        o.setName("Wall-Renamed");
        o.setPosition(new Vector2D(9, 9));

        assertEquals("Wall-Renamed", o.getName());
        assertEquals(new Vector2D(9, 9), o.getPosition());
    }

    /**
     * Rack stores name and position from the two-argument constructor.
     */
    @Test
    public void testRackNameAndPosition() {
        Rack r = new Rack("Rack-A", new Vector2D(7, 3));
        assertEquals("Rack-A", r.getName());
        assertEquals(new Vector2D(7, 3), r.getPosition());
    }

    /**
     * Rack auto-generates a non-null UUID.
     */
    @Test
    public void testRackAutoUuid() {
        Rack r = new Rack("Rack-B", new Vector2D(0, 0));
        assertNotNull(r.getId());
    }

    /**
     * Rack explicit-UUID constructor stores the provided UUID.
     */
    @Test
    public void testRackExplicitUuid() {
        UUID id = UUID.randomUUID();
        Rack r = new Rack(id, "Rack-C", new Vector2D(5, 5));
        assertEquals(id, r.getId());
    }

    /**
     * Rack explicit-UUID constructor also preserves name and position.
     */
    @Test
    public void testRackExplicitUuidStoresNameAndPosition() {
        UUID id = UUID.randomUUID();
        Rack r = new Rack(id, "Rack-C", new Vector2D(5, 5));
        assertEquals("Rack-C", r.getName());
        assertEquals(new Vector2D(5, 5), r.getPosition());
    }

    /**
     * Rack is a subtype of MapEntity.
     */
    @Test
    public void testRackIsMapEntity() {
        Rack r = new Rack("Rack", new Vector2D(0, 0));
        assertInstanceOf(MapEntity.class, r);
    }

    /**
     * Rack is NOT an Obstacle (these are separate entity types with different
     * traversability semantics).
     */
    @Test
    public void testRackIsNotObstacle() {
        Rack r = new Rack("Rack", new Vector2D(0, 0));
        assertNotEquals(Obstacle.class, r.getClass(),
                "Rack must not be an Obstacle – they have different traversability semantics");
    }

    /**
     * Two Rack instances have distinct auto-generated UUIDs.
     */
    @Test
    public void testRackUniqueIds() {
        Rack a = new Rack("R1", new Vector2D(0, 0));
        Rack b = new Rack("R2", new Vector2D(0, 0));
        assertNotEquals(a.getId(), b.getId());
    }

    /**
     * Rack inherits mutable name and position setters from MapEntity.
     */
    @Test
    public void testRackInheritedSetters() {
        Rack r = new Rack("Rack", new Vector2D(0, 0));

        r.setName("Rack-Renamed");
        r.setPosition(new Vector2D(8, 8));

        assertEquals("Rack-Renamed", r.getName());
        assertEquals(new Vector2D(8, 8), r.getPosition());
    }

    /**
     * Rack.take(...) is currently a no-op stub and should not throw.
     */
    @Test
    public void testRackTakeDoesNotThrow() {
        Rack r = new Rack("Rack", new Vector2D(0, 0));

        assertDoesNotThrow(() -> r.take(new Object()));
        assertEquals("Rack", r.getName());
        assertEquals(new Vector2D(0, 0), r.getPosition());
    }
}
