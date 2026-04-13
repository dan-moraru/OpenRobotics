# Manual Task Assignment, Number-of-Tasks Rename, and Post-Completion Play Gate

Date: 2026-04-13
Branch: `feature/robot-properties-panel`

## Summary

Three related changes to the simulation setup and editor:

1. **Play gate after completion** — once a simulation has finished (tick > 0, no queued tasks, all robots idle), the Play button must be disabled until the run is reset.
2. **UI label rename** — `Max Tasks` → `Number of Tasks` in the Setup screen. UI-only change (no Java, config, or DB rename).
3. **Manual task assignment** — racks gain an opt-in *Manual Dropoff Assignment* mode. In manual mode, the user edits a UE5-style array of valid dropoff points on the rack properties panel; boxes are distributed across the pool using round-robin.

## Scope

In scope:

- New boolean field on `Rack`: `manualDropoffAssignment`.
- New rack properties panel UI (replacing the current stub) with box-count spinner, manual-mode checkbox, and a dynamic dropoff-pool array.
- Viewport "assignment-picking" mode with visual highlight + tip-bar message.
- Round-robin dropoff assignment when manual mode is active.
- Play-time validation: manual-mode racks with no non-null dropoffs block play with an error dialog.
- `isFinished()` helper on `SimulationEngine`; play-button disable when finished.
- Tooltips on Setup "Number of Tasks" field and on the rack array header.
- DTO change (`RackDTO.manualDropoffAssignment`) so configs round-trip.

Out of scope:

- Renaming Java fields, config keys, DB columns related to `maxTasks`.
- Any change to coordination, logging, or dispatch logic beyond round-robin inside `TaskGenerator`.
- New task-generation modes beyond round-robin pool distribution.

## Design

### 1. Play gate after completion

**Definition of "finished":** `tickCounter > 0 && dispatcher.getAllQueuedTasks().isEmpty() && every robot's state == RobotState.IDLE`.

**`SimulationEngine.isFinished()`** — new public method computing the predicate above. No side effects.

**UI wiring in [`SimulationController`](open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java):**

- In `onPlay` (line ~1580), guard at the top: `if (engine != null && engine.isFinished()) { log("Simulation already complete — press Stop to reset."); return; }`.
- At the end of each tick inside `startLoop`, check `engine.isFinished()`; if true, call `stopLoop()`, set `running = false`, and set `playBtn.setDisable(true)`.
- `onStop` (and any reset path) re-enables `playBtn`.

**Invariant:** while `engine.isFinished()` returns true, `running` is false and `playBtn` is disabled.

### 2. "Number of Tasks" rename (UI-only)

Change in [`SetupScreen.fxml`](open-robotics/src/main/resources/com/openrobotics/fxml/SetupScreen.fxml) at line ~104:

- `<Label text="Max Tasks" .../>` → `<Label text="Number of Tasks" .../>`
- Add a `<Tooltip>` to the field with text:
  > "Number of tasks randomly generated when the simulation starts. Racks that use *Manual Dropoff Assignment* override this for their own boxes."

No other file changes. `maxTasksField` fx:id, `DEFAULT_MAX_TASKS`, `onResetMaxTasks`, tests, and JSON keys stay as-is.

### 3. Manual task assignment

#### 3.1 Model

[`Rack.java`](open-robotics/src/main/java/com/openrobotics/map/entities/environment/Rack.java) gains one field:

```java
private boolean manualDropoffAssignment = false;
public boolean isManualDropoffAssignment() { return manualDropoffAssignment; }
public void setManualDropoffAssignment(boolean v) { this.manualDropoffAssignment = v; }
```

`validDropoffIds` semantics become explicit:

- `manualDropoffAssignment == false` → the field is **preserved but ignored** at task-generation time. All delivery stations are valid.
- `manualDropoffAssignment == true` → the field is the authoritative pool. Null entries are placeholders. Unresolvable UUIDs (station deleted between edit and play) are treated the same as null.

Invariants on `Rack` (enforce in setters):

- `boxCount >= 1`
- `validDropoffIds != null` (may be empty)

#### 3.2 Persistence

[`SimulationConfigDTO.RackDTO`](open-robotics/src/main/java/com/openrobotics/io/SimulationConfigDTO.java) gains:

```java
public boolean manualDropoffAssignment = false;
```

Default `false` keeps older configs backward-compatible. The existing `List<String> validDropoffIds` already round-trips. Marshaling in `SimulationController` (DTO↔Rack) must carry the new flag.

#### 3.3 Properties panel UI

Replaces the stub at [`SimulationController`:1415-1417](open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java#L1415-L1417).

Rows rendered into `propertiesPanel` when `selectedEntity instanceof Rack`:

1. **Number of boxes** — `Spinner<Integer>`, min 1, max 99, bound to `rack.boxCount` via listener.
2. **Manual dropoff assignment** — `CheckBox` bound to `rack.manualDropoffAssignment`. Toggling OFF hides (but does not clear) the array. Toggling ON reveals it.
3. **Valid dropoff points** — visible iff the checkbox is ON. A `VBox` containing:
   - Header `HBox`: label "Valid Dropoff Points" + `+` button + info icon with tooltip *"Boxes are distributed across the valid dropoff points using round-robin. Null slots are ignored. At least one non-null slot is required to play."*
   - For each entry in `rack.validDropoffIds` (including nulls), one row:
     - `Label` showing `"[i]"` index.
     - `Label` showing either the delivery station's name + `"(x,y)"` or `"(unset)"` in italic grey if null.
     - `Button "Assign"` — enters assignment-picking mode for this slot.
     - `Button "✕"` — removes the slot.
   - `+` appends a new `null` entry.

Any change (spinner, checkbox, +/✕, assignment result) mutates the `Rack` instance in place and re-renders the panel.

#### 3.4 Assignment-picking mode

New controller state:

```java
private Rack pickingRack = null;   // null when not picking
private int  pickingSlot = -1;
```

`Assign` button: sets `pickingRack` and `pickingSlot`, writes a tip via `ViewportTips` (`"Click a delivery station to assign it to slot #N. Press Esc or click elsewhere to cancel."`), sets viewport cursor to `Cursor.CROSSHAIR`, and applies a highlight overlay to all `DeliveryStation` tiles using the existing selection-highlight styling pattern (same CSS class as current `selectedEntity`, scoped while picking is active).

Viewport click handler (existing path that sets `selectedEntity`) is augmented: **if `pickingRack != null`**, intercept the click first:

- If the clicked entity is a `DeliveryStation`: `rack.validDropoffIds.set(pickingSlot, station.getId())`, clear picking state, re-render properties panel.
- Anything else (including Esc or click on empty tile): cancel — clear picking state, do not modify the slot.

Clearing picking state always: reset cursor, clear tip, remove highlight overlay.

Invariant: `pickingRack != null ⇔ pickingSlot >= 0 && pickingSlot < pickingRack.validDropoffIds.size()`.

#### 3.5 Task generation

[`TaskGenerator.generateTasks`](open-robotics/src/main/java/com/openrobotics/task/TaskGenerator.java) per-rack branch changes (line ~51):

```text
if (!rack.isManualDropoffAssignment()) {
    validStations = allStations
    assignmentMode = RANDOM
} else {
    validStations = rack.validDropoffIds filtered: non-null AND resolvable via stationById
    assignmentMode = ROUND_ROBIN
    // no fallback — empty validStations means generateTasks() should not have been called;
    // play-time validation (3.6) prevents this path.
}
```

For the loop over `boxCount`:

- Random mode: unchanged (`validStations.get(random.nextInt(validStations.size()))`).
- Round-robin mode: `validStations.get(i % validStations.size())`, where `i` is the per-rack box index.

Invariant: if `rack.isManualDropoffAssignment()` is true and `generateTasks` is reached, `validStations` is non-empty.

#### 3.6 Play-time validation

Before the existing `TaskGenerator.generateRandomTasks` call in [`SimulationController.onPlay`](open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java#L1619):

```text
errors = []
for rack in map.entities where rack is Rack and rack.isManualDropoffAssignment():
    pool = non-null validDropoffIds that resolve to a current DeliveryStation
    if pool.isEmpty():
        errors.add(rack.getName())
if errors non-empty:
    show error dialog listing the rack names
    running = false
    return
```

The dialog is blocking; no tasks are generated and the simulation does not start.

## Error Handling

- Invalid manual-mode rack → blocking error dialog at Play, no partial state.
- Finished simulation + Play pressed → log message, Play button stays disabled.
- Null/invalid UUIDs in `validDropoffIds` that weren't caught by validation (e.g. station deleted after validation passed) → filtered out silently inside `TaskGenerator`; invariant on 3.5 guarantees `validStations` non-empty before the filter runs.
- Spinner bounds enforced by JavaFX; setter clamps `boxCount >= 1` regardless.

## Testing

Manual UI testing (no unit test harness for JavaFX panels in scope):

- Play after simulation completes: button disabled, pressing does nothing.
- Rename: Setup label reads "Number of Tasks", tooltip visible on hover.
- Rack properties: spinner edits boxCount; checkbox toggles array visibility without clearing entries; `+` adds null slot; `✕` removes; Assign enters picking, highlights stations, accepts delivery-station click, cancels on other clicks.
- Round-robin generation: rack with boxCount=5 and pool of [A, B] produces dropoffs [A, B, A, B, A].
- Manual mode with all-null pool blocks Play with a dialog naming the rack.
- Config save/load round-trips `manualDropoffAssignment`.

Existing unit/integration tests continue to pass (they reference `maxTasksField` / `maxTasks` which are unchanged).

## File touch list

- [`Rack.java`](open-robotics/src/main/java/com/openrobotics/map/entities/environment/Rack.java) — add `manualDropoffAssignment`.
- [`SimulationConfigDTO.java`](open-robotics/src/main/java/com/openrobotics/io/SimulationConfigDTO.java) — add `RackDTO.manualDropoffAssignment`.
- [`SimulationEngine.java`](open-robotics/src/main/java/com/openrobotics/simulationcore/SimulationEngine.java) — add `isFinished()`.
- [`SimulationController.java`](open-robotics/src/main/java/com/openrobotics/controllers/SimulationController.java) — Play gate, rack properties panel, assignment-picking mode, Play-time validation, DTO marshaling.
- [`TaskGenerator.java`](open-robotics/src/main/java/com/openrobotics/task/TaskGenerator.java) — honor `manualDropoffAssignment`, round-robin in manual mode.
- [`SetupScreen.fxml`](open-robotics/src/main/resources/com/openrobotics/fxml/SetupScreen.fxml) — label + tooltip.
