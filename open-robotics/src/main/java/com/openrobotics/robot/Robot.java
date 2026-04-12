package com.openrobotics.robot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrobotics.AppState;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.db.recordbuilders.SimLogRecordBuilder;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.eventtypes.RobotEvent;
import com.openrobotics.logging.eventtypes.TaskEvent;
import com.openrobotics.db.recordbuilders.WorkloadTaskRecordBuilder;
import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.robot.navigation.NavigationStrategy;
import com.openrobotics.simulationcore.MoveIntention;
import com.openrobotics.robot.sensors.Sensor;
import com.openrobotics.robot.sensors.SensorStrategy;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import com.openrobotics.map.Vector2D;

import java.util.HashMap;
import java.util.UUID;

import static com.openrobotics.robot.RobotState.IDLE;

// robot entity — extends mapentity with robot-specific state (uml 3.3.4)
// inherits uuid, name, position, update() hook
public class Robot extends MapEntity {
    private float battery;
    private NavigationStrategy nav;
    private SensorStrategy sensor;
    private RobotState state;
    private Task currentTask;
    private int stuckTicks;

    // movement fields
    private Vector2D previousPosition; // saved before move for stuck detection
    private boolean hasPickedUp; // false = going to pickup, true = going to dropoff
    private Vector2D chargerTarget; // overrides task target when low battery
    private int loadingTicksRemaining; // pickup dwell timer
    private int unloadingTicksRemaining; // dropoff dwell timer
    private Sensor lastScan; // keeps track of the last scan record of the robot
    private boolean rerouteAttemptedForCurrentTask; // one reroute budget per task
    private Vector2D rerouteAvoidTile; // temporary avoid hint used by the next planning attempt
    private Vector2D lastRequestedNextTile; // raw pre-coordination next tile requested this tick

    // lifetime stats — accumulated during update(), read by results screen
    private int totalDistanceMoved;
    private int tasksCompleted;
    private int totalIdleTicks;
    private int totalMovingTicks;
    private int totalChargingTicks;
    private float totalEnergyConsumed;

    private RobotConfig config;

    // takes Vector2D position, delegates to MapEntity via super()
    public Robot(String name, Vector2D position) {
        super(name, position);
        this.config = RobotConfig.defaults();
        initMovementFields();
    }

    // constructor for loading robots
    public Robot(UUID id, String name, Vector2D position) {
        super(id, name, position);
        this.config = RobotConfig.defaults();
        initMovementFields();
    }

    private void initMovementFields() {
        this.battery = config.batteryCapacity;
        this.nav = null;
        this.sensor = null;
        this.state = IDLE;
        this.currentTask = null;
        this.stuckTicks = 0;
        this.previousPosition = null;
        this.hasPickedUp = false;
        this.chargerTarget = null;
        this.loadingTicksRemaining = 0;
        this.unloadingTicksRemaining = 0;
        this.rerouteAttemptedForCurrentTask = false;
        this.rerouteAvoidTile = null;
        this.lastRequestedNextTile = null;
        this.totalDistanceMoved = 0;
        this.tasksCompleted = 0;
        this.totalIdleTicks = 0;
        this.totalMovingTicks = 0;
        this.totalChargingTicks = 0;
        this.totalEnergyConsumed = 0;
    }

    // getters for all fields
    public float getBattery() { return battery; }
    public NavigationStrategy getNav() { return nav; }
    public SensorStrategy getSensor() { return sensor; }
    public Sensor getLastScan() { return lastScan; }
    public RobotState getState() { return state; }
    public Task getCurrentTask() { return currentTask; }
    public int getStuckTicks() { return stuckTicks; }
    public Vector2D getPreviousPosition() { return previousPosition; }
    public boolean isHasPickedUp() { return hasPickedUp; }
    public void setHasPickedUp(boolean hasPickedUp) { this.hasPickedUp = hasPickedUp; }
    public int getLoadingTicksRemaining() { return loadingTicksRemaining; }
    public int getUnloadingTicksRemaining() { return unloadingTicksRemaining; }
    public boolean hasRerouteAttemptedForCurrentTask() { return rerouteAttemptedForCurrentTask; }
    public Vector2D getRerouteAvoidTile() { return rerouteAvoidTile; }
    public Vector2D getLastRequestedNextTile() { return lastRequestedNextTile; }

    // lifetime stats getters
    public int getTotalDistanceMoved() { return totalDistanceMoved; }
    public int getTasksCompleted() { return tasksCompleted; }
    public int getTotalIdleTicks() { return totalIdleTicks; }
    public int getTotalMovingTicks() { return totalMovingTicks; }
    public int getTotalChargingTicks() { return totalChargingTicks; }
    public float getTotalEnergyConsumed() { return totalEnergyConsumed; }

    public RobotConfig getConfig() { return config; }
    public void setConfig(RobotConfig config) {
        this.config = config != null ? config : RobotConfig.defaults();
    }

    // setters for mutable robot state
    public void setBattery(float battery) { this.battery = battery; }
    public void setNav(NavigationStrategy nav) { this.nav = nav; }
    public void setSensor(SensorStrategy sensor) { this.sensor = sensor; }
    public void setState(RobotState state) { this.state = state; }
    public void setCurrentTask(Task currentTask) {
        // Each task gets one reroute budget, so changing tasks resets the reroute state.
        if (!sameTask(this.currentTask, currentTask)) {
            clearDeadlockRerouteState();
        }
        this.currentTask = currentTask;
    }
    public void setStuckTicks(int stuckTicks) { this.stuckTicks = stuckTicks; }

    public boolean startDeadlockRerouteAttempt() {
        // A reroute only makes sense if the robot actually tried to enter a different tile.
        if (!canStartDeadlockRerouteAttempt()) {
            return false;
        }

        rerouteAttemptedForCurrentTask = true;
        rerouteAvoidTile = lastRequestedNextTile;
        stuckTicks = 0;
        nav.reset(this);
        state = RobotState.MOVING;
        return true;
    }

    public void recoverFromDeadlock() {
        // Recovery returns the robot to a clean idle state for the next assignment attempt.
        if (nav != null) {
            nav.reset(this);
        }
        setCurrentTask(null);
        hasPickedUp = false;
        chargerTarget = null;
        loadingTicksRemaining = 0;
        unloadingTicksRemaining = 0;
        previousPosition = null;
        stuckTicks = 0;
        lastRequestedNextTile = null;
        state = IDLE;
    }

    // returns the current navigation target based on priority:
    // charger (if set) > pickup (if not picked up) > dropoff
    public Vector2D getTarget() {
        if (chargerTarget != null) return chargerTarget;
        if (currentTask == null) return null;
        if (!hasPickedUp) return currentTask.getPickupLocation();
        return currentTask.getDropoffLocation();
    }

    // returns a move intention, called once per tick before collision resolution
    public MoveIntention getNextMove(Map map) {
        // Update data sensor first
        if (this.sensor != null) {
            this.lastScan = this.sensor.scan(this, map);
        }

        previousPosition = getPosition(); // save for stuck detection in update()
        Tile fromTile = map.getTile(getPosition().getX(), getPosition().getY());
        if (fromTile == null) {
            throw new IllegalStateException("Robot is on an invalid tile at "
                    + getPosition().getX() + "," + getPosition().getY());
        }

        // safety net: idle robot with a task should start moving
        if (state == IDLE && currentTask != null) {
            state = RobotState.MOVING;
        }

        // only moving robots produce real move intentions
        if (state != RobotState.MOVING) {
            return rememberRequestedMove(new MoveIntention(fromTile, fromTile, this));
        }

        // low battery; check if already on a charger or redirect to one
        if (needsCharging(config.lowBatteryThreshold)) {
            // check via instanceof so it works even if chargerTarget was never set
            boolean onCharger = false;
            for (MapEntity e : map.getEntitiesAt(getPosition())) {
                if (e instanceof ChargingStation) {
                    onCharger = true;
                    break;
                }
            }
            if (onCharger) {
                state = RobotState.CHARGING;
                chargerTarget = null; // clear override

                // Logging charging start event
                SimLogRecordBuilder chargingStartRecordBuilder = new SimLogRecordBuilder(AppState.getEngine().getRunId(), AppState.getEngine().getTickCounter(), getId(), getPosition().getX(), getPosition().getY());
                SimLogRecord record = chargingStartRecordBuilder.buildChargeStartRecord("{'batteryLevel': " + "'" + battery + "'" + "}");
                Logger.logRobotEvent(RobotEvent.CHARGE_START, record);

                return rememberRequestedMove(new MoveIntention(fromTile, fromTile, this));
            }
            // find nearest charger and override nav target
            if (chargerTarget == null) {
                Vector2D nearest = map.findNearestChargingStation(getPosition());
                if (nearest != null) {
                    chargerTarget = nearest;
                } else {
                    System.err.println("[Robot] No charging station found for robot " + getName()
                            + " at " + getPosition() + " with battery=" + battery);
                    state = IDLE;
                    return rememberRequestedMove(new MoveIntention(fromTile, fromTile, this));
                }
            }
        } else {
            chargerTarget = null; // battery ok, clear charger override
        }

        // delegate to nav strategy, null-safe if no strategy is set
        if (nav != null && getTarget() != null) {
            return rememberRequestedMove(nav.getNextMove(this, map));
        }

        // no nav or no target. stay in place
        return rememberRequestedMove(new MoveIntention(fromTile, fromTile, this));
    }

    // dispatcher checks this to find robots that can accept tasks
    public boolean isAvailable() {
        return state == IDLE && currentTask == null;
    }

    // battery decreases per move, floors at 0 to prevent negative values
    public void consumeEnergy(float amount) {
        this.battery = Math.max(0, this.battery - amount);
    }

    // robot seeks charging station when below threshold (design doc 3.4.3)
    public boolean needsCharging(float threshold) {
        return this.battery < threshold;
    }

    // returns the enum state name — keeps old api working for tests/ui
    public String getStatus() {
        return state.name();
    }

    // per-tick update hook — called by sim engine after position commit
    @Override
    public void update(Map map) {
        switch (state) {
            case CHARGING:
                totalChargingTicks++;
                battery = Math.min(config.batteryCapacity, battery + config.chargePerTick); // cap at capacity
                if (battery >= config.batteryCapacity) {
                    // fully charged — resume task or go idle

                    state = (currentTask != null) ? RobotState.MOVING : RobotState.IDLE;

                    // Logging charging end event
                    SimLogRecordBuilder chargingEndRecordBuilder = new SimLogRecordBuilder(AppState.getEngine().getRunId(), AppState.getEngine().getTickCounter(), getId(), getPosition().getX(), getPosition().getY());
                    SimLogRecord record = chargingEndRecordBuilder.buildChargeEndRecord("{'batteryLevel': " + "'" + battery + "'" + "}");
                    Logger.logRobotEvent(RobotEvent.CHARGE_END, record);
                }
                break;

            case LOADING:
                loadingTicksRemaining--;
                if (loadingTicksRemaining <= 0) {
                    hasPickedUp = true; // pickup done, now head to dropoff
                    state = RobotState.MOVING;
                }
                break;

            case UNLOADING:
                unloadingTicksRemaining--;
                if (unloadingTicksRemaining <= 0) {
                    // delivery done — mark task completed and reset
                    if (currentTask != null) {
                        currentTask.setStatus(TaskStatus.COMPLETED);
                        tasksCompleted++;

                        // Logging task completion event
                        int currentTick = AppState.getEngine().getTickCounter();
                        WorkloadTaskRecordBuilder taskCompletionRecordBuilder = new WorkloadTaskRecordBuilder(AppState.getEngine().getRunId(), currentTask);
                        WorkloadTaskRecord record = taskCompletionRecordBuilder.buildTaskCompletionRecord(currentTick);
                        Logger.logTaskEvent(TaskEvent.TASK_COMPLETED, record);
                    }
                    setCurrentTask(null);
                    hasPickedUp = false;
                    state = IDLE;
                }
                break;

            case MOVING:
                totalMovingTicks++;

                // check if robots battery has died
                if (battery <= 0) {
                    state = RobotState.BATTER_DEAD;

                    // Logging battery death event
                    SimLogRecordBuilder batterDeathRecordBuilder = new SimLogRecordBuilder(AppState.getEngine().getRunId(), AppState.getEngine().getTickCounter(), getId(), getPosition().getX(), getPosition().getY());
                    SimLogRecord record = batterDeathRecordBuilder.buildBatteryDeathRecord();
                    Logger.logRobotEvent(RobotEvent.BATTERY_DEATH, record);

                    break;
                }

                // check if robot actually moved this tick
                if (previousPosition != null && !getPosition().equals(previousPosition)) {
                    consumeEnergy(config.energyPerMove);
                    totalEnergyConsumed += config.energyPerMove;
                    totalDistanceMoved++;
                    stuckTicks = 0;

                    // Logging robot movement execution event
                    SimLogRecordBuilder movementRecordBuilder = new SimLogRecordBuilder(AppState.getEngine().getRunId(), AppState.getEngine().getTickCounter(), getId(), getPosition().getX(), getPosition().getY());
                    SimLogRecord moveRecord = movementRecordBuilder.buildMoveExecutionRecord();
                    Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, moveRecord);

                    if (rerouteAvoidTile != null) {
                        // The first successful move after rerouting means the temporary avoid hint is no longer needed.
                        rerouteAvoidTile = null;

                        // Logging robot deadlock resolution event via rerouting
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            java.util.Map<String, String> details = new HashMap<>();
                            details.put("resolutionMethod", "reroute");
                            String json = mapper.writeValueAsString(details);

                            SimLogRecordBuilder recordBuilder = new SimLogRecordBuilder(AppState.getEngine().getRunId(), AppState.getEngine().getTickCounter(), getId(), getPosition().getX(), getPosition().getY());
                            SimLogRecord record = recordBuilder.buildDeadlockResolutionRecord(json);
                            Logger.logRobotEvent(RobotEvent.DEADLOCK_RESOLVED, record);
                        } catch (JsonProcessingException e) {
                            System.out.println("Error serializing deadlock resolution details for logging: " + e.getMessage());
                        }
                    }

                } else {
                    stuckTicks++;
                }

                if (currentTask != null && chargerTarget == null) {
                    Vector2D target = getTarget();
                    boolean arrived = isAtTarget(map, target);
                    if (arrived) {
                        if (!hasPickedUp) {
                            state = RobotState.LOADING;
                            loadingTicksRemaining = config.loadingTicks;
                        } else {
                            state = RobotState.UNLOADING;
                            unloadingTicksRemaining = config.unloadingTicks;
                        }
                    }
                }
                break;

            case IDLE:
                totalIdleTicks++;
                break;

            default:
                break;
        }
    }

    // returns true if the robot has arrived at target.
    // racks are solid — arrival means standing adjacent (distance 1), not on top.
    // all other targets require being on the same tile.
    private boolean isAtTarget(Map map, Vector2D target) {
        if (target == null || map == null) return false;
        return map.isRackAt(target)
            ? getPosition().manhattanDistance(target) == 1
            : getPosition().equals(target);
    }

    // readable debug output
    @Override
    public String toString() {
        return "Robot{name=" + getName() + ", pos=" + getPosition()
                + ", battery=" + battery + ", state=" + state + "}";
    }

    private MoveIntention rememberRequestedMove(MoveIntention intention) {
        // Deadlock rerouting needs to know which tile the robot originally wanted before
        // coordination or collision handling changed the outcome of the tick.
        if (intention == null || intention.getFromTile() == null || intention.getToTile() == null) {
            // Missing tile data means there is no meaningful move request to remember.
            lastRequestedNextTile = null;
            return intention;
        }

        if (intention.getFromTile().getX() == intention.getToTile().getX()
                && intention.getFromTile().getY() == intention.getToTile().getY()) {
            // Waiting in place does not create an alternate tile for reroute recovery to avoid.
            lastRequestedNextTile = null;
        } else {
            // Store the raw requested destination so the first deadlock recovery can avoid it once.
            lastRequestedNextTile = intention.getToTile().getPosition();
        }
        return intention;
    }

    private void clearDeadlockRerouteState() {
        rerouteAttemptedForCurrentTask = false;
        rerouteAvoidTile = null;
        lastRequestedNextTile = null;
    }

    public boolean canStartDeadlockRerouteAttempt() {
        if (currentTask == null || rerouteAttemptedForCurrentTask || nav == null || lastRequestedNextTile == null) {
            return false;
        }

        Vector2D target = getTarget();
        if (target == null) return false;

        // Do not reroute if the robot is already standing on the target tile.
        // Rack-adjacency arrival is detected by Robot.update() which transitions to LOADING;
        // recoverDeadlockedRobots only runs on MOVING robots, so that case is already excluded
        // before this method is ever called > no need to pass a null map here.
        if (getPosition().equals(target)) {
            return false;
        }

        return !lastRequestedNextTile.equals(target);
    }

    private boolean sameTask(Task a, Task b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
