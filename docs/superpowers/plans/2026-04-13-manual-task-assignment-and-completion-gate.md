# Manual Task Assignment, Number-of-Tasks Rename, and Play Gate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Block Play after a simulation completes, rename the Setup "Max Tasks" label to "Number of Tasks", and add an opt-in manual dropoff-assignment mode to racks (with UE5-style array editing in the properties panel, viewport picking, and round-robin task generation).

**Architecture:** Three loosely coupled vertical slices. (1) `SimulationEngine.isFinished()` + play-button disable/guard in `SimulationController`. (2) FXML label + tooltip edit. (3) New `Rack.manualDropoffAssignment` field, DTO round-trip, rack properties panel UI, viewport "assignment-picking" mode (leveraging existing `tipLabel` + selection-highlight patterns), and round-robin branch inside `TaskGenerator`. Play-time validation ties (1) and (3) together.

**Tech Stack:** Java 17, JavaFX 21 (FXML + CSS), Maven, JUnit 5, TestFX.

**Spec:** `docs/superpowers/specs/2026-04-13-manual-task-assignment-and-completion-gate-design.md`

---

## File Structure

**Modified:**
- `open-robotics/src/main/java/com/openrobotics/map/entities/environment/Rack.java` — new `manualDropoffAssignment` field with getter/setter
- `open-robotics/src/main/java/com/openrobotics/io/SimulationConfigDTO.java` — new `RackDTO.manualDropoffAssignment` field
- `open-robotics/src/main/java/com/openrobotics/simulationcore/SimulationEngine.java` — new `isFinished()` method; DTO↔Rack marshaling carries the new flag
- `open-robotics/src/main/java/com/openrobotics/task/TaskGenerator.java` — honor `manualDropoffAssignment`; round-robin in manual mode
- `open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java` — play gate in `onPlay`/`startLoop`, rack properties panel, picking-mode state, play-time validation
- `open-robotics/src/main/resources/com/openrobotics/fxml/SetupScreen.fxml` — label rename + tooltip

**Tests created/modified:**
- `open-robotics/src/test/java/com/openrobotics/map/entities/environment/RackTest.java` — new
- `open-robotics/src/test/java/com/openrobotics/simulationcore/SimulationEngineFinishedTest.java` — new
- `open-robotics/src/test/java/com/openrobotics/task/TaskGeneratorManualModeTest.java` — new

---

## Task 1: Add `manualDropoffAssignment` field to `Rack`

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/map/entities/environment/Rack.java`
- Create: `open-robotics/src/test/java/com/openrobotics/map/entities/environment/RackTest.java`

- [ ] **Step 1: Write the failing test**

Create `open-robotics/src/test/java/com/openrobotics/map/entities/environment/RackTest.java`:

```java
package com.openrobotics.map.entities.environment;

import com.openrobotics.map.Vector2D;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RackTest {

    @Test
    void newRackDefaultsToRandomAssignment() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        assertFalse(rack.isManualDropoffAssignment(),
                "New racks must default to random (non-manual) mode");
    }

    @Test
    void manualAssignmentFlagIsPersistent() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        rack.setManualDropoffAssignment(true);
        assertTrue(rack.isManualDropoffAssignment());
        rack.setManualDropoffAssignment(false);
        assertFalse(rack.isManualDropoffAssignment());
    }

    @Test
    void togglingManualOffPreservesValidDropoffIds() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        java.util.UUID a = java.util.UUID.randomUUID();
        rack.getValidDropoffIds().add(a);
        rack.setManualDropoffAssignment(true);
        rack.setManualDropoffAssignment(false);
        assertEquals(java.util.List.of(a), rack.getValidDropoffIds(),
                "Toggling manual mode must not clear the existing pool");
    }

    @Test
    void boxCountSetterEnforcesMinimumOfOne() {
        Rack rack = new Rack("r1", new Vector2D(0, 0));
        rack.setBoxCount(0);
        assertEquals(1, rack.getBoxCount());
        rack.setBoxCount(-5);
        assertEquals(1, rack.getBoxCount());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `open-robotics/`:
```bash
./mvnw -pl . test -Dtest=RackTest
```
Expected: compile error — `isManualDropoffAssignment()` / `setManualDropoffAssignment(boolean)` do not exist.

- [ ] **Step 3: Add the field to `Rack.java`**

Edit `open-robotics/src/main/java/com/openrobotics/map/entities/environment/Rack.java` — add the field below the existing `validDropoffIds` field, and add getter/setter below the existing `setValidDropoffIds`:

```java
    private boolean manualDropoffAssignment = false;

    public boolean isManualDropoffAssignment() { return manualDropoffAssignment; }
    public void setManualDropoffAssignment(boolean manualDropoffAssignment) {
        this.manualDropoffAssignment = manualDropoffAssignment;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./mvnw -pl . test -Dtest=RackTest
```
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/map/entities/environment/Rack.java \
        open-robotics/src/test/java/com/openrobotics/map/entities/environment/RackTest.java
git commit -m "feat(rack): add manualDropoffAssignment flag"
```

---

## Task 2: Add `manualDropoffAssignment` to `RackDTO` and round-trip via `SimulationEngine`

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/io/SimulationConfigDTO.java`
- Modify: `open-robotics/src/main/java/com/openrobotics/simulationcore/SimulationEngine.java` (addRacksToMap ~line 253, toDto rack branch ~line 343)

- [ ] **Step 1: Extend `RackDTO`**

Edit `SimulationConfigDTO.java`, inside `public static class RackDTO extends MapEntityDTO`:

```java
    public static class RackDTO extends MapEntityDTO {
        public int boxCount = 1;
        public List<String> validDropoffIds; // UUID strings, null = all stations valid
        public boolean manualDropoffAssignment = false;
    }
```

- [ ] **Step 2: Hydrate the flag on load (`addRacksToMap`)**

Edit `SimulationEngine.java` around line 258 (inside `addRacksToMap`). After `rack.setBoxCount(eDto.boxCount);` and the existing `validDropoffIds` hydration block, add:

```java
            rack.setManualDropoffAssignment(eDto.manualDropoffAssignment);
```

- [ ] **Step 3: Serialize the flag on save**

In `SimulationEngine.java` around line 349 (the `entity instanceof Rack rack` branch of `toDto`/equivalent), after `rDto.boxCount = rack.getBoxCount();`, add:

```java
                rDto.manualDropoffAssignment = rack.isManualDropoffAssignment();
```

- [ ] **Step 4: Smoke-build to catch any compile issues**

```bash
./mvnw -pl . compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/io/SimulationConfigDTO.java \
        open-robotics/src/main/java/com/openrobotics/simulationcore/SimulationEngine.java
git commit -m "feat(dto): round-trip rack manualDropoffAssignment through config"
```

---

## Task 3: `SimulationEngine.isFinished()`

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/simulationcore/SimulationEngine.java`
- Create: `open-robotics/src/test/java/com/openrobotics/simulationcore/SimulationEngineFinishedTest.java`

- [ ] **Step 1: Write the failing test**

Create `open-robotics/src/test/java/com/openrobotics/simulationcore/SimulationEngineFinishedTest.java`:

```java
package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotConfig;
import com.openrobotics.robot.RobotState;
import com.openrobotics.map.Vector2D;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineFinishedTest {

    private SimulationEngine newEngine(Robot[] robots, Dispatcher dispatcher) {
        Map map = new Map(java.util.UUID.randomUUID(), 5, 5);
        return new SimulationEngine(map, robots, dispatcher,
                CoordinationPolicy.noOp(), "t", 100, 1000, 42L, 10);
    }

    @Test
    void notFinishedAtTickZero() {
        Dispatcher d = new Dispatcher();
        SimulationEngine e = newEngine(new Robot[0], d);
        assertFalse(e.isFinished(), "tick==0 is never finished");
    }

    @Test
    void notFinishedWhenTasksStillQueued() {
        Dispatcher d = new Dispatcher();
        d.addTasks(List.of(new Task(1, new Vector2D(0,0), new Vector2D(1,1), 1)));
        SimulationEngine e = newEngine(new Robot[0], d);
        // Advance tick artificially via a manual tick() or by exposing a test hook:
        e.tick();
        assertFalse(e.isFinished(), "pending tasks => not finished");
    }

    @Test
    void finishedWhenTickAdvancedAllIdleNoTasks() {
        Robot r = new Robot("r1", new Vector2D(0,0), RobotConfig.defaults());
        r.setState(RobotState.IDLE);
        Dispatcher d = new Dispatcher();
        SimulationEngine e = newEngine(new Robot[]{r}, d);
        e.tick();
        assertTrue(e.isFinished());
    }

    @Test
    void notFinishedWhenARobotIsNonIdle() {
        Robot r = new Robot("r1", new Vector2D(0,0), RobotConfig.defaults());
        r.setState(RobotState.MOVING);
        Dispatcher d = new Dispatcher();
        SimulationEngine e = newEngine(new Robot[]{r}, d);
        e.tick();
        assertFalse(e.isFinished());
    }
}
```

> Note: if `Robot`/`Dispatcher`/`Task` constructors in this repo differ, adjust the test setup minimally to compile. The **semantics of the four cases** are what matters, not the exact constructor shapes.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw -pl . test -Dtest=SimulationEngineFinishedTest
```
Expected: compile error — `isFinished()` does not exist.

- [ ] **Step 3: Add `isFinished()` to `SimulationEngine`**

In `SimulationEngine.java`, near `getTickCounter()` (around line 700), add:

```java
    /**
     * A run is finished when the clock has started, every robot is idle, and
     * the dispatcher has no more queued tasks. tickCounter==0 is never
     * finished so Play on a fresh engine is always allowed.
     */
    public boolean isFinished() {
        if (tickCounter <= 0) return false;
        if (dispatcher == null || !dispatcher.getAllQueuedTasks().isEmpty()) return false;
        if (robots == null) return true;
        for (Robot r : robots) {
            if (r == null) continue;
            if (r.getState() != com.openrobotics.robot.RobotState.IDLE) return false;
        }
        return true;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./mvnw -pl . test -Dtest=SimulationEngineFinishedTest
```
Expected: 4 tests pass. If any fail due to unrelated test-setup compile errors, fix the test's construction calls to match the actual `Robot`/`Dispatcher`/`Task` signatures in this repo — **do not weaken `isFinished`**.

- [ ] **Step 5: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/simulationcore/SimulationEngine.java \
        open-robotics/src/test/java/com/openrobotics/simulationcore/SimulationEngineFinishedTest.java
git commit -m "feat(engine): add isFinished() predicate"
```

---

## Task 4: Wire the play gate in `SimulationController`

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java` (`onPlay` at line ~1580, `onStop` at ~1671, and the tick step inside `startLoop`)

- [ ] **Step 1: Guard `onPlay` against a finished engine**

In `onPlay()` (line ~1580), right after the existing null-engine guard:

```java
    @FXML
    private void onPlay() {
        if (engine == null) {
            log("\u26a0 No simulation loaded. Return to Setup and load a config.");
            return;
        }
        if (engine.isFinished()) {
            log("\u26a0 Simulation already complete. Press Stop to reset before playing again.");
            if (playBtn != null) playBtn.setDisable(true);
            return;
        }
        // ... existing code continues ...
```

- [ ] **Step 2: Disable the play button when a tick transitions the engine to finished**

Locate `startLoop()` (grep for `startLoop` in the file). Inside the per-tick callback (the body of the `KeyFrame`/`AnimationTimer`/whatever schedules `engine.tick()`), after the `engine.tick()` call and UI refresh, add:

```java
            if (engine != null && engine.isFinished()) {
                running = false;
                paused = false;
                stopLoop();
                if (playBtn != null) playBtn.setDisable(true);
                if (simStatusLabel != null) {
                    simStatusLabel.setText("COMPLETE");
                    simStatusLabel.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold;");
                }
                log("Simulation complete — all tasks done and all robots idle. Press Stop to reset.");
            }
```

- [ ] **Step 3: Re-enable the play button on stop/reset**

In `onStop()` (line ~1671), after `stopLoop();`, add:

```java
        if (playBtn != null) playBtn.setDisable(false);
```

Also search the file for `initialSnapshotPath` reset paths and `resetSimulation`/`performReset`-style methods (if present); anywhere tickCounter is reset to 0 add the same `playBtn.setDisable(false)` line. If no such method exists, `onStop` alone is sufficient because it's the user-facing reset.

- [ ] **Step 4: Manual smoke test**

```bash
./mvnw -pl . -DskipTests package
./mvnw -pl . javafx:run
```
Load any map with one rack + one delivery station + zero robots. Press Play. Because there are no robots, tick advances but no tasks are processed. Simulate a completion by removing tasks via whatever the existing path is (if too involved, skip manual and rely on unit tests plus code review).

Expected: after `engine.isFinished()` flips true, `playBtn` is disabled and status reads "COMPLETE". Clicking Stop re-enables the button.

- [ ] **Step 5: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java
git commit -m "feat(sim-ctrl): block play after completion, disable play button"
```

---

## Task 5: Rename "Max Tasks" label to "Number of Tasks" in Setup FXML

**Files:**
- Modify: `open-robotics/src/main/resources/com/openrobotics/fxml/SetupScreen.fxml` (line ~104)

- [ ] **Step 1: Edit the label and add a tooltip**

Replace line 104 and the following `TextField` tag so the block reads:

```xml
<HBox styleClass="setup-prop-row-b" alignment="CENTER_LEFT" spacing="5">
    <Label text="Number of Tasks" styleClass="prop-label-bold"/>
    <TextField fx:id="maxTasksField" text="10"
               HBox.hgrow="ALWAYS" maxWidth="Infinity">
        <tooltip>
            <Tooltip text="Number of tasks randomly generated when the simulation starts. Racks that use Manual Dropoff Assignment override this for their own boxes."/>
        </tooltip>
    </TextField>
    <Button text="↺" styleClass="button-reset" onAction="#onResetMaxTasks"/>
</HBox>
```

If `<?import javafx.scene.control.Tooltip?>` is not already at the top of the FXML, add it alongside the other `javafx.scene.control.*` imports.

- [ ] **Step 2: Run the existing Setup controller tests to verify nothing broke**

```bash
./mvnw -pl . test -Dtest=SetupControllerTest
```
Expected: PASS (tests reference `maxTasksField` by fx:id, which is unchanged).

- [ ] **Step 3: Commit**

```bash
git add open-robotics/src/main/resources/com/openrobotics/fxml/SetupScreen.fxml
git commit -m "feat(setup-ui): rename Max Tasks label to Number of Tasks with tooltip"
```

---

## Task 6: Round-robin in `TaskGenerator` when rack is in manual mode

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/task/TaskGenerator.java` (line ~51 onward)
- Create: `open-robotics/src/test/java/com/openrobotics/task/TaskGeneratorManualModeTest.java`

- [ ] **Step 1: Write the failing test**

Create `open-robotics/src/test/java/com/openrobotics/task/TaskGeneratorManualModeTest.java`:

```java
package com.openrobotics.task;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.DeliveryStation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskGeneratorManualModeTest {

    private Map buildMap() {
        Map m = new Map(UUID.randomUUID(), 10, 10);
        return m;
    }

    @Test
    void randomModeIgnoresValidDropoffIds() {
        Map m = buildMap();
        DeliveryStation a = new DeliveryStation("A", new Vector2D(5, 0));
        DeliveryStation b = new DeliveryStation("B", new Vector2D(6, 0));
        m.addEntity(a); m.addEntity(b);
        Rack rack = new Rack("rack", new Vector2D(1, 1));
        rack.setBoxCount(10);
        rack.getValidDropoffIds().add(a.getId()); // even though restricted, random mode ignores this
        rack.setManualDropoffAssignment(false);
        m.addEntity(rack);

        List<Task> tasks = new TaskGenerator(m, 42L).generateTasks(10);

        boolean usedB = tasks.stream().anyMatch(t -> t.getDropoffLocation().equals(b.getPosition()));
        assertTrue(usedB, "Random mode must draw from all stations, not just validDropoffIds");
    }

    @Test
    void manualModeUsesRoundRobinOverNonNullPool() {
        Map m = buildMap();
        DeliveryStation a = new DeliveryStation("A", new Vector2D(5, 0));
        DeliveryStation b = new DeliveryStation("B", new Vector2D(6, 0));
        m.addEntity(a); m.addEntity(b);
        Rack rack = new Rack("rack", new Vector2D(1, 1));
        rack.setBoxCount(5);
        rack.getValidDropoffIds().add(a.getId());
        rack.getValidDropoffIds().add(null); // null slot must be skipped
        rack.getValidDropoffIds().add(b.getId());
        rack.setManualDropoffAssignment(true);
        m.addEntity(rack);

        List<Task> tasks = new TaskGenerator(m, 42L).generateTasks(100);

        assertEquals(5, tasks.size());
        List<Vector2D> drops = new ArrayList<>();
        for (Task t : tasks) drops.add(t.getDropoffLocation());
        assertEquals(List.of(a.getPosition(), b.getPosition(),
                             a.getPosition(), b.getPosition(),
                             a.getPosition()), drops,
                "Round-robin must cycle through non-null pool in order");
    }

    @Test
    void manualModeWithUnresolvableIdsFiltersThem() {
        Map m = buildMap();
        DeliveryStation a = new DeliveryStation("A", new Vector2D(5, 0));
        m.addEntity(a);
        Rack rack = new Rack("rack", new Vector2D(1, 1));
        rack.setBoxCount(3);
        rack.getValidDropoffIds().add(UUID.randomUUID()); // not in the map
        rack.getValidDropoffIds().add(a.getId());
        rack.setManualDropoffAssignment(true);
        m.addEntity(rack);

        List<Task> tasks = new TaskGenerator(m, 42L).generateTasks(100);

        assertEquals(3, tasks.size());
        for (Task t : tasks) {
            assertEquals(a.getPosition(), t.getDropoffLocation());
        }
    }
}
```

> If `DeliveryStation`'s constructor differs, adjust the test construction lines to match. The `Task` getter name `getDropoffLocation()` is used by existing code (see `TaskGenerator`) so it should exist.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw -pl . test -Dtest=TaskGeneratorManualModeTest
```
Expected: `manualModeUsesRoundRobinOverNonNullPool` fails because current code picks randomly; `randomModeIgnoresValidDropoffIds` likely fails because current code already honors `validDropoffIds` regardless of the flag.

- [ ] **Step 3: Change `TaskGenerator.generateTasks` to honor the flag and round-robin**

Replace the per-rack block starting at the current line 52 (the `Resolve valid dropoff stations for this rack` comment) through line 80 (end of the box loop) with:

```java
            // Resolve dropoff stations for this rack based on its assignment mode.
            List<DeliveryStation> validStations;
            boolean roundRobin;
            if (!rack.isManualDropoffAssignment()) {
                validStations = new ArrayList<>(allStations);
                roundRobin = false;
            } else {
                validStations = new ArrayList<>();
                for (UUID uuid : rack.getValidDropoffIds()) {
                    if (uuid == null) continue;
                    DeliveryStation ds = stationById.get(uuid);
                    if (ds != null) validStations.add(ds);
                }
                roundRobin = true;
                if (validStations.isEmpty()) {
                    // Defensive: play-time validation should prevent this; skip rack silently.
                    continue;
                }
            }

            // skip racks with no accessible adjacent tile (e.g. surrounded by walls)
            if (!map.hasTraversableAdjacentTile(rack.getPosition())) {
                continue;
            }

            // generate up to boxCount tasks from this rack
            for (int i = 0; i < rack.getBoxCount(); i++) {
                if (tasks.size() >= maxCount) break outer;
                DeliveryStation station = roundRobin
                        ? validStations.get(i % validStations.size())
                        : validStations.get(random.nextInt(validStations.size()));
                int priority = random.nextInt(3) + 1;
                tasks.add(new Task(nextTaskId++,
                    new Vector2D(rack.getPosition().getX(), rack.getPosition().getY()),
                    new Vector2D(station.getPosition().getX(), station.getPosition().getY()),
                    priority));
            }
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./mvnw -pl . test -Dtest=TaskGeneratorManualModeTest
```
Expected: 3 tests pass. Also run the full test suite to catch regressions:

```bash
./mvnw -pl . test
```
Expected: no new failures (existing tests that don't touch `manualDropoffAssignment` should continue to pass because the default is `false` and the random branch now bypasses `validDropoffIds`).

- [ ] **Step 5: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/task/TaskGenerator.java \
        open-robotics/src/test/java/com/openrobotics/task/TaskGeneratorManualModeTest.java
git commit -m "feat(task-gen): round-robin dropoff assignment in manual mode"
```

---

## Task 7: Rack properties panel — box count spinner + manual-mode checkbox

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java` (replace rack branch at line 1415-1418)

- [ ] **Step 1: Replace the Rack stub with a spinner + checkbox**

In `showPropertiesFor(MapEntity entity)`, change the `else if (entity instanceof Rack)` branch at line 1415 to:

```java
        } else if (entity instanceof Rack rack) {
            // ── Box count ──
            HBox boxCountBox = new HBox(8);
            boxCountBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Spinner<Integer> boxCountSpinner = new Spinner<>(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, rack.getBoxCount()));
            boxCountSpinner.setPrefWidth(80);
            boxCountSpinner.setEditable(true);
            boxCountSpinner.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV == null || oldV == null || newV.equals(oldV)) return;
                if (guardEditor("change box count")) {
                    boxCountSpinner.getValueFactory().setValue(oldV);
                    return;
                }
                rack.setBoxCount(newV);
                persistEditorChanges();
            });
            boxCountBox.getChildren().addAll(new Label("Number of boxes:"), boxCountSpinner);
            propertiesPanel.getChildren().add(boxCountBox);

            // ── Manual assignment checkbox ──
            HBox manualBox = new HBox(8);
            manualBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            CheckBox manualCheck = new CheckBox("Manual dropoff assignment");
            manualCheck.setSelected(rack.isManualDropoffAssignment());
            VBox dropoffArrayBox = new VBox(4);
            dropoffArrayBox.setVisible(rack.isManualDropoffAssignment());
            dropoffArrayBox.setManaged(rack.isManualDropoffAssignment());
            manualCheck.selectedProperty().addListener((obs, oldV, newV) -> {
                if (guardEditor("toggle manual assignment")) {
                    manualCheck.setSelected(oldV);
                    return;
                }
                rack.setManualDropoffAssignment(newV);
                dropoffArrayBox.setVisible(newV);
                dropoffArrayBox.setManaged(newV);
                renderRackDropoffArray(rack, dropoffArrayBox);
                persistEditorChanges();
            });
            manualBox.getChildren().add(manualCheck);
            propertiesPanel.getChildren().add(manualBox);

            // Array container (populated in Task 8)
            renderRackDropoffArray(rack, dropoffArrayBox);
            propertiesPanel.getChildren().add(dropoffArrayBox);
        }
```

Also add at the top of the file, near the existing `javafx.scene.control.*` imports, if missing:

```java
import javafx.scene.control.CheckBox;
```

(`Spinner`, `SpinnerValueFactory`, `HBox`, `VBox`, `Label` are already imported.)

- [ ] **Step 2: Add a stub `renderRackDropoffArray` method so the file compiles**

Anywhere inside `SimulationController` (e.g. right below `showPropertiesFor`), add:

```java
    /** Renders the manual-mode dropoff-pool array into the given container. Filled in by Task 8. */
    private void renderRackDropoffArray(Rack rack, VBox container) {
        container.getChildren().clear();
        // Implementation follows in Task 8.
    }
```

- [ ] **Step 3: Compile**

```bash
./mvnw -pl . compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java
git commit -m "feat(sim-ctrl): rack props panel with box count + manual toggle"
```

---

## Task 8: Rack properties panel — dropoff array (add/remove/assign rows)

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java` (the stub `renderRackDropoffArray` from Task 7; also add picking-mode fields)

- [ ] **Step 1: Add picking-mode fields and a tip helper**

Near the other controller fields (around `selectedEntity` at line 187), add:

```java
    private Rack pickingRack = null;
    private int  pickingSlot = -1;
```

- [ ] **Step 2: Implement `renderRackDropoffArray`**

Replace the stub from Task 7 with:

```java
    /**
     * Renders the manual-mode dropoff-pool array into the given container.
     * One row per slot (including null slots). A + button appends, ✕ removes,
     * and Assign enters viewport picking mode for that slot.
     */
    private void renderRackDropoffArray(Rack rack, VBox container) {
        container.getChildren().clear();

        // Header row: label + info tooltip + add button
        HBox header = new HBox(6);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label headerLabel = new Label("Valid Dropoff Points");
        headerLabel.setStyle("-fx-font-weight: bold;");
        Tooltip.install(headerLabel, new Tooltip(
                "Boxes are distributed across the valid dropoff points using round-robin. "
                + "Null slots are ignored. At least one non-null slot is required to play."));
        Button addBtn = new Button("+");
        addBtn.setOnAction(ev -> {
            if (guardEditor("add dropoff slot")) return;
            rack.getValidDropoffIds().add(null);
            renderRackDropoffArray(rack, container);
            persistEditorChanges();
        });
        header.getChildren().addAll(headerLabel, addBtn);
        container.getChildren().add(header);

        java.util.Map<UUID, DeliveryStation> stationById = new java.util.HashMap<>();
        if (engine != null && engine.getMap() != null) {
            for (MapEntity e : engine.getMap().getEntities()) {
                if (e instanceof DeliveryStation ds) stationById.put(ds.getId(), ds);
            }
        }

        List<UUID> ids = rack.getValidDropoffIds();
        for (int i = 0; i < ids.size(); i++) {
            final int slotIndex = i;
            UUID id = ids.get(i);
            HBox row = new HBox(6);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label idxLabel = new Label("[" + i + "]");
            idxLabel.setStyle("-fx-text-fill: #666;");

            String display;
            if (id == null) {
                display = "(unset)";
            } else {
                DeliveryStation ds = stationById.get(id);
                if (ds == null) {
                    display = "(deleted station)";
                } else {
                    display = ds.getName() + " (" + (int) ds.getPosition().getX()
                              + ", " + (int) ds.getPosition().getY() + ")";
                }
            }
            Label displayLabel = new Label(display);
            if (id == null || stationById.get(id) == null) {
                displayLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #999;");
            }

            Button assignBtn = new Button("Assign");
            assignBtn.setOnAction(ev -> beginDropoffPicking(rack, slotIndex));

            Button removeBtn = new Button("\u2715");
            removeBtn.setOnAction(ev -> {
                if (guardEditor("remove dropoff slot")) return;
                rack.getValidDropoffIds().remove(slotIndex);
                renderRackDropoffArray(rack, container);
                persistEditorChanges();
            });

            row.getChildren().addAll(idxLabel, displayLabel, assignBtn, removeBtn);
            container.getChildren().add(row);
        }
    }
```

Make sure these imports exist at the top of the file (add any that are missing):

```java
import javafx.scene.control.Tooltip;
import javafx.scene.control.Button;
import com.openrobotics.map.entities.station.DeliveryStation;
```

- [ ] **Step 3: Add `beginDropoffPicking` stub (filled in Task 9)**

Right after `renderRackDropoffArray`, add:

```java
    /** Enters viewport picking mode for a rack dropoff slot. Implementation in Task 9. */
    private void beginDropoffPicking(Rack rack, int slotIndex) {
        this.pickingRack = rack;
        this.pickingSlot = slotIndex;
        if (tipLabel != null) {
            tipLabel.setText("TIP: Click a delivery station to assign it to slot #"
                    + slotIndex + ". Click elsewhere to cancel.");
        }
        viewportStack.setCursor(javafx.scene.Cursor.CROSSHAIR);
        drawViewport();
    }

    private void cancelDropoffPicking() {
        this.pickingRack = null;
        this.pickingSlot = -1;
        if (tipLabel != null) tipLabel.setText("");
        viewportStack.setCursor(javafx.scene.Cursor.DEFAULT);
        drawViewport();
    }
```

- [ ] **Step 4: Compile**

```bash
./mvnw -pl . compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java
git commit -m "feat(sim-ctrl): rack dropoff array UI with +/assign/remove"
```

---

## Task 9: Viewport picking-mode click handling and highlight

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java` (`onViewportMousePressed` at line ~1006; the entity draw loop around line 673)

- [ ] **Step 1: Intercept clicks when picking is active**

At the very top of `onViewportMousePressed(MouseEvent e)` (just after the focus/position bookkeeping on lines 1007-1011), insert:

```java
        if (pickingRack != null && e.getButton() == MouseButton.PRIMARY) {
            MapEntity hit = entityAtScreenPos(e.getX(), e.getY());
            if (hit instanceof DeliveryStation ds) {
                // Slot index must still be valid (user could have removed rows mid-pick).
                if (pickingSlot >= 0 && pickingSlot < pickingRack.getValidDropoffIds().size()) {
                    pickingRack.getValidDropoffIds().set(pickingSlot, ds.getId());
                    log("Assigned " + ds.getName() + " to slot " + pickingSlot
                            + " of rack " + pickingRack.getName() + ".");
                    Rack rackRef = pickingRack;
                    cancelDropoffPicking();
                    // Re-render the rack properties panel to reflect the assignment.
                    if (selectedEntity == rackRef) showPropertiesFor(rackRef);
                    persistEditorChanges();
                } else {
                    cancelDropoffPicking();
                }
            } else {
                cancelDropoffPicking();
                log("Dropoff assignment cancelled.");
            }
            return;
        }
```

(`DeliveryStation` and `MouseButton` are already imported.)

- [ ] **Step 2: Add a highlight overlay on delivery stations while picking**

Locate the entity-drawing loop that contains the "Selection highlight" comment at line 673 (inside `drawViewport`/`drawEntities`). Immediately after the existing selection highlight for the entity branch (for each iterated entity, find where `selectedEntity == entity` is drawn), add:

```java
            if (pickingRack != null && entity instanceof DeliveryStation) {
                gc.setStroke(OBJECT_SELECTION_COLOR);
                gc.setLineWidth(Math.max(2.0, tileSize * 0.1));
                gc.strokeRect(sx + 1, sy + 1, tileSize - 2, tileSize - 2);
            }
```

Make sure `sx`/`sy`/`tileSize` are in scope at that point (they are, because the existing selection highlight uses them).

> If the existing loop uses different local variable names, adapt the three lines to match. The intent: draw a bright rectangle around every `DeliveryStation` tile while `pickingRack != null`.

- [ ] **Step 3: Compile and smoke test**

```bash
./mvnw -pl . compile
./mvnw -pl . -DskipTests package
./mvnw -pl . javafx:run
```

Manually:
1. Load a map with at least one rack and two delivery stations.
2. Select the rack, check "Manual dropoff assignment", click `+` twice.
3. Click Assign on slot 0 — tip bar should read "TIP: Click a delivery station...", cursor should change, both delivery stations should show the highlight outline.
4. Click a delivery station — slot 0 should update with its name+coords.
5. Click Assign on slot 1 then click on empty floor — tip should clear, nothing should change.

- [ ] **Step 4: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java
git commit -m "feat(sim-ctrl): viewport picking mode for rack dropoff assignment"
```

---

## Task 10: Play-time validation for manual-mode racks

**Files:**
- Modify: `open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java` (`onPlay` around line 1619, before the `TaskGenerator.generateRandomTasks` call)

- [ ] **Step 1: Insert the validation block**

Immediately before the existing block at line 1619 (`if (engine.getDispatcher().getAllQueuedTasks().isEmpty() ...`), add:

```java
            // ── Validate manual-mode racks before generating tasks ──
            if (engine.getMap() != null) {
                java.util.Set<UUID> stationIds = new java.util.HashSet<>();
                for (MapEntity me : engine.getMap().getEntities()) {
                    if (me instanceof DeliveryStation ds) stationIds.add(ds.getId());
                }
                List<String> offenders = new ArrayList<>();
                for (MapEntity me : engine.getMap().getEntities()) {
                    if (me instanceof Rack r && r.isManualDropoffAssignment()) {
                        boolean hasValid = false;
                        for (UUID uid : r.getValidDropoffIds()) {
                            if (uid != null && stationIds.contains(uid)) { hasValid = true; break; }
                        }
                        if (!hasValid) offenders.add(r.getName());
                    }
                }
                if (!offenders.isEmpty()) {
                    running = false;
                    paused  = false;
                    if (playBtn  != null) playBtn.setStyle("");
                    if (pauseBtn != null) pauseBtn.setStyle("");
                    if (simStatusLabel != null) {
                        simStatusLabel.setText("STOPPED");
                        simStatusLabel.setStyle("-fx-text-fill: #D6453D; -fx-font-weight: bold;");
                    }
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Cannot start simulation");
                    alert.setHeaderText("Manual-mode racks have no valid dropoff points");
                    alert.setContentText("The following racks use Manual Dropoff Assignment but their pool is empty or all-null:\n\n  \u2022 "
                            + String.join("\n  \u2022 ", offenders)
                            + "\n\nAssign at least one delivery station per rack, or disable Manual Dropoff Assignment.");
                    alert.showAndWait();
                    log("\u26a0 Play aborted: " + offenders.size() + " rack(s) have an empty manual dropoff pool.");
                    return;
                }
            }
```

Add the import if missing:

```java
import javafx.scene.control.Alert;
```

- [ ] **Step 2: Compile**

```bash
./mvnw -pl . compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test**

1. Create a rack, enable manual mode, add one slot, leave it null.
2. Press Play → an error dialog should list the rack by name, the simulation should not start.
3. Assign a delivery station to the slot.
4. Press Play → simulation starts normally.

- [ ] **Step 4: Commit**

```bash
git add open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java
git commit -m "feat(sim-ctrl): validate manual-mode racks before play"
```

---

## Task 11: Full regression + final commit

- [ ] **Step 1: Run the full test suite**

```bash
./mvnw -pl . test
```
Expected: all tests pass.

- [ ] **Step 2: Full build**

```bash
./mvnw -pl . package
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual end-to-end scenario**

1. Launch: `./mvnw -pl . javafx:run`.
2. Setup screen: confirm label reads "Number of Tasks" with tooltip.
3. Load a map with 1 rack and 2 delivery stations.
4. Rack properties: set Number of boxes = 4, enable Manual, `+` twice, Assign each slot to a different station.
5. Play: tasks generated round-robin (4 tasks: A,B,A,B).
6. Let it finish → play button disables, status = COMPLETE.
7. Press Stop → button re-enables.
8. Save config → reopen → verify manual flag and pool persist.

- [ ] **Step 4: Final commit if anything was fixed during the smoke test**

```bash
git status
git add -p
git commit -m "chore: regression fixups for manual task assignment feature"
```

Skip this step if nothing needed fixing.

---

## Self-review notes

- Spec §1 (play gate): Tasks 3 + 4.
- Spec §2 (rename): Task 5.
- Spec §3.1 (model): Task 1.
- Spec §3.2 (persistence): Task 2.
- Spec §3.3 (properties panel UI): Tasks 7 + 8.
- Spec §3.4 (picking mode): Task 9 (with fields seeded in Task 8).
- Spec §3.5 (round-robin): Task 6.
- Spec §3.6 (validation): Task 10.
- Final regression: Task 11.

All method names used downstream (`isFinished`, `isManualDropoffAssignment`, `setManualDropoffAssignment`, `renderRackDropoffArray`, `beginDropoffPicking`, `cancelDropoffPicking`, `pickingRack`, `pickingSlot`) are defined in the task that introduces them or earlier.
