package com.openrobotics.task;

import com.openrobotics.map.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Task} and its concrete subclass {@link UnloadBox}.
 *
 * <p>Covers construction, all getters and setters, the {@code equals} contract
 * (ID-based), {@code compareTo} / priority-queue ordering, and toString.
 */
public class TaskTest {

    // Helpers

    private static final Vector2D PICKUP  = new Vector2D(1, 2);
    private static final Vector2D DROPOFF = new Vector2D(5, 6);

    private Task makeTask(int id, int priority) {
        return new Task(id, PICKUP, DROPOFF, priority);
    }

    /**
     * getId() should return the id supplied to the constructor.
     */
    @Test
    public void testGetId() {
        assertEquals(42, makeTask(42, 1).getId());
    }

    /**
     * getPickupLocation() should return the pickup position.
     */
    @Test
    public void testGetPickupLocation() {
        assertEquals(PICKUP, makeTask(1, 0).getPickupLocation());
    }

    /**
     * getDropoffLocation() should return the dropoff position.
     */
    @Test
    public void testGetDropoffLocation() {
        assertEquals(DROPOFF, makeTask(1, 0).getDropoffLocation());
    }

    /**
     * getPriority() should return the priority supplied to the constructor.
     */
    @Test
    public void testGetPriority() {
        assertEquals(5, makeTask(1, 5).getPriority());
    }

    /**
     * A new task's status must be {@link TaskStatus#PENDING}.
     */
    @Test
    public void testInitialStatusIsPending() {
        assertEquals(TaskStatus.PENDING, makeTask(1, 0).getStatus());
    }

    /**
     * setPriority() should update the value returned by getPriority().
     */
    @Test
    public void testSetPriority() {
        Task t = makeTask(1, 3);
        t.setPriority(99);
        assertEquals(99, t.getPriority());
    }

    /**
     * setStatus() should update the status correctly.
     */
    @Test
    public void testSetStatus() {
        Task t = makeTask(1, 0);
        t.setStatus(TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, t.getStatus());

        t.setStatus(TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, t.getStatus());

        t.setStatus(TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, t.getStatus());
    }

    /**
     * Two tasks with the same id must be equal regardless of other fields.
     */
    @Test
    public void testEqualsSameId() {
        Task a = new Task(7, new Vector2D(0, 0), new Vector2D(1, 1), 1);
        Task b = new Task(7, new Vector2D(9, 9), new Vector2D(2, 2), 99);
        assertEquals(a, b, "Tasks with the same id must be equal");
    }

    /**
     * Equality should remain true even after mutating non-ID fields.
     */
    @Test
    public void testEqualsIgnoresPriorityAndStatusMutations() {
        Task a = makeTask(7, 1);
        Task b = makeTask(7, 99);

        a.setStatus(TaskStatus.COMPLETED);
        b.setStatus(TaskStatus.FAILED);
        b.setPriority(-10);

        assertEquals(a, b);
    }

    /**
     * Two tasks with different ids must not be equal.
     */
    @Test
    public void testNotEqualDifferentId() {
        assertNotEquals(makeTask(1, 0), makeTask(2, 0));
    }

    /**
     * A task is equal to itself (reflexive).
     */
    @Test
    public void testEqualsReflexive() {
        Task t = makeTask(3, 0);
        assertEquals(t, t);
    }

    /**
     * Equality should be symmetric for tasks with the same ID.
     */
    @Test
    public void testEqualsSymmetric() {
        Task a = makeTask(11, 1);
        Task b = new Task(11, new Vector2D(8, 8), new Vector2D(9, 9), 7);

        assertEquals(a, b);
        assertEquals(b, a);
    }

    /**
     * Equality should be transitive for tasks with the same ID.
     */
    @Test
    public void testEqualsTransitive() {
        Task a = makeTask(15, 1);
        Task b = new Task(15, new Vector2D(2, 2), new Vector2D(3, 3), 5);
        Task c = new UnloadBox(15, new Vector2D(4, 4), new Vector2D(5, 5), 9);

        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a, c);
    }

    /**
     * A task must not be equal to null.
     */
    @Test
    public void testNotEqualNull() {
        assertNotEquals(null, makeTask(1, 0));
    }

    /**
     * A task must not be equal to an object of a different type.
     */
    @Test
    public void testNotEqualDifferentType() {
        assertNotEquals("Task{id=1}", makeTask(1, 0));
    }

    /**
     * Equal tasks must have the same hash code because hashing is ID-based too.
     */
    @Test
    public void testHashCodeMatchesEqualsContract() {
        Task a = makeTask(23, 1);
        Task b = new Task(23, new Vector2D(9, 9), new Vector2D(10, 10), 999);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * Changing mutable fields must not change the hash code because the ID is final.
     */
    @Test
    public void testHashCodeUnaffectedByMutableFields() {
        Task task = makeTask(24, 3);
        int before = task.hashCode();

        task.setPriority(100);
        task.setStatus(TaskStatus.COMPLETED);

        assertEquals(before, task.hashCode());
    }

    /**
     * A task with higher priority value is "less than" a task with lower priority value
     * (natural ordering; PriorityQueue will dequeue higher-priority-number tasks first).
     */
    @Test
    public void testCompareToHigherFirst() {
        Task low = makeTask(1, 1);
        Task high = makeTask(2, 10);
        assertTrue(high.compareTo(low) < 0, "Higher priority number should come first");
        assertTrue(low.compareTo(high) > 0);
    }

    /**
     * Tasks with equal priority values must compare as equal.
     */
    @Test
    public void testCompareToEqualPriority() {
        assertEquals(0, makeTask(1, 5).compareTo(makeTask(2, 5)));
    }

    /**
     * compareTo should remain consistent after a task's priority changes.
     */
    @Test
    public void testCompareToReflectsUpdatedPriority() {
        Task task = makeTask(1, 1);
        Task other = makeTask(2, 5);

        assertTrue(task.compareTo(other) > 0);

        task.setPriority(10);

        assertTrue(task.compareTo(other) < 0);
    }

    /**
     * Negative priorities are currently allowed and should still order correctly.
     */
    @Test
    public void testPriorityQueueOrderWithNegativePriorities() {
        PriorityQueue<Task> pq = new PriorityQueue<>();
        Task zero = makeTask(1, 0);
        Task negativeOne = makeTask(2, -1);
        Task negativeTen = makeTask(3, -10);

        pq.add(negativeTen);
        pq.add(zero);
        pq.add(negativeOne);

        assertEquals(zero, pq.poll());
        assertEquals(negativeOne, pq.poll());
        assertEquals(negativeTen, pq.poll());
    }

    /**
     * PriorityQueue respects Task's natural ordering: higher priority number is polled first.
     */
    @Test
    public void testPriorityQueueOrder() {
        PriorityQueue<Task> pq = new PriorityQueue<>();
        Task high = makeTask(1, 10);
        Task medium = makeTask(2, 5);
        Task low = makeTask(3, 1);

        pq.add(high);
        pq.add(low);
        pq.add(medium);

        assertEquals(high, pq.poll(), "Task with priority=10 must be dequeued first");
        assertEquals(medium, pq.poll(), "Task with priority=5 must be dequeued second");
        assertEquals(low,pq.poll(), "Task with priority=1 must be dequeued last");
    }

    /**
     * toString() should include the id, status, and priority.
     */
    @Test
    public void testToString() {
        Task t = makeTask(9, 3);
        String s = t.toString();
        assertTrue(s.contains("9"), "toString must contain the task id");
        assertTrue(s.contains("PENDING"), "toString must contain the initial status");
        assertTrue(s.contains("3"), "toString must contain the priority");
    }

    /**
     * toString should reflect later mutations as well.
     */
    @Test
    public void testToStringReflectsUpdatedState() {
        Task t = makeTask(12, 3);
        t.setStatus(TaskStatus.IN_PROGRESS);
        t.setPriority(8);

        assertEquals("Task{id=12, status=IN_PROGRESS, priority=8}", t.toString());
    }

    /**
     * Null locations are currently accepted because the constructor does not validate them.
     */
    @Test
    public void testConstructorAllowsNullLocations() {
        Task t = new Task(30, null, null, 4);

        assertNull(t.getPickupLocation());
        assertNull(t.getDropoffLocation());
        assertEquals(TaskStatus.PENDING, t.getStatus());
    }

    /**
     * Null status is currently accepted because setStatus performs no validation.
     */
    @Test
    public void testSetStatusAllowsNull() {
        Task t = makeTask(31, 1);

        t.setStatus(null);

        assertNull(t.getStatus());
        assertEquals("Task{id=31, status=null, priority=1}", t.toString());
    }

    /**
     * UnloadBox delegates to Task's constructor; all fields are set correctly.
     */
    @Test
    public void testUnloadBoxConstruction() {
        UnloadBox ub = new UnloadBox(100, PICKUP, DROPOFF, 7);
        assertEquals(100,   ub.getId());
        assertEquals(PICKUP,  ub.getPickupLocation());
        assertEquals(DROPOFF, ub.getDropoffLocation());
        assertEquals(7,     ub.getPriority());
        assertEquals(TaskStatus.PENDING, ub.getStatus());
    }

    /**
     * UnloadBox is a subtype of Task.
     */
    @Test
    public void testUnloadBoxIsTask() {
        UnloadBox ub = new UnloadBox(1, PICKUP, DROPOFF, 0);
        assertInstanceOf(Task.class, ub);
    }

    /**
     * UnloadBox equality is still id-based, inherited from Task.
     */
    @Test
    public void testUnloadBoxEqualityById() {
        UnloadBox a = new UnloadBox(5, PICKUP, DROPOFF, 1);
        UnloadBox b = new UnloadBox(5, new Vector2D(9,9), new Vector2D(8,8), 99);
        assertEquals(a, b);
    }

    /**
     * Equality is ID-based even across Task and UnloadBox instances.
     */
    @Test
    public void testTaskEqualsUnloadBoxWithSameId() {
        Task task = makeTask(41, 1);
        UnloadBox unloadBox = new UnloadBox(41, new Vector2D(9, 9), new Vector2D(8, 8), 99);

        assertEquals(task, unloadBox);
        assertEquals(unloadBox, task);
    }

    /**
     * UnloadBox participates correctly in a PriorityQueue.
     */
    @Test
    public void testUnloadBoxInPriorityQueue() {
        PriorityQueue<Task> pq = new PriorityQueue<>();
        pq.add(new UnloadBox(1, PICKUP, DROPOFF, 5));
        pq.add(new UnloadBox(2, PICKUP, DROPOFF, 1));

        assertEquals(1, pq.poll().getId(), "UnloadBox with higher priority number should be polled first");
    }
}
