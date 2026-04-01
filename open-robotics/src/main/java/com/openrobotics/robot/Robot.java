package com.openrobotics.robot;

import com.openrobotics.AppState;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.eventtypes.TaskEvent;
import com.openrobotics.logging.recordbuilders.WorkloadTaskRecordBuilder;
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
import java.util.UUID;

import com.openrobotics.simulationcore.MoveIntention;

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

    // lifetime stats — accumulated during update(), read by results screen
    private int totalDistanceMoved;
    private int tasksCompleted;
    private int totalIdleTicks;
    private int totalMovingTicks;
    private int totalChargingTicks;
    private float totalEnergyConsumed;

    // provisional constants. future config task may override
    private static final float ENERGY_PER_MOVE = 1.0f;
    private static final float LOW_BATTERY_THRESHOLD = 20.0f;
    private static final float CHARGE_PER_TICK = 5.0f;
    private static final int DEFAULT_LOADING_TICKS = 1;
    private static final int DEFAULT_UNLOADING_TICKS = 1;

    // takes Vector2D position, delegates to MapEntity via super()
    public Robot(String name, Vector2D position) {
        super(name, position);
        initMovementFields();
    }

    // constructor for loading robots
    public Robot(UUID id, String name, Vector2D position) {
        super(id, name, position);
        initMovementFields();
    }

    private void initMovementFields() {
        this.battery = 100.0f;
        this.nav = null;
        this.sensor = null;
        this.state = RobotState.IDLE;
        this.currentTask = null;
        this.stuckTicks = 0;
        this.previousPosition = null;
        this.hasPickedUp = false;
        this.chargerTarget = null;
        this.loadingTicksRemaining = 0;
        this.unloadingTicksRemaining = 0;
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

    // lifetime stats getters
    public int getTotalDistanceMoved() { return totalDistanceMoved; }
    public int getTasksCompleted() { return tasksCompleted; }
    public int getTotalIdleTicks() { return totalIdleTicks; }
    public int getTotalMovingTicks() { return totalMovingTicks; }
    public int getTotalChargingTicks() { return totalChargingTicks; }
    public float getTotalEnergyConsumed() { return totalEnergyConsumed; }

    // setters for mutable robot state
    public void setBattery(float battery) { this.battery = battery; }
    public void setNav(NavigationStrategy nav) { this.nav = nav; }
    public void setSensor(SensorStrategy sensor) { this.sensor = sensor; }
    public void setState(RobotState state) { this.state = state; }
    public void setCurrentTask(Task currentTask) { this.currentTask = currentTask; }
    public void setStuckTicks(int stuckTicks) { this.stuckTicks = stuckTicks; }

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
        if (state == RobotState.IDLE && currentTask != null) {
            state = RobotState.MOVING;
        }

        // only moving robots produce real move intentions
        if (state != RobotState.MOVING) {
            return new MoveIntention(fromTile, fromTile, this);
        }

        // low battery; check if already on a charger or redirect to one
        if (needsCharging(LOW_BATTERY_THRESHOLD)) {
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
                return new MoveIntention(fromTile, fromTile, this);
            }
            // find nearest charger and override nav target
            if (chargerTarget == null) {
                Vector2D nearest = map.findNearestChargingStation(getPosition());
                if (nearest != null) {
                    chargerTarget = nearest;
                } else {
                    System.err.println("[Robot] No charging station found for robot " + getName()
                            + " at " + getPosition() + " with battery=" + battery);
                    state = RobotState.IDLE;
                    return new MoveIntention(fromTile, fromTile, this);
                }
            }
        } else {
            chargerTarget = null; // battery ok, clear charger override
        }

        // delegate to nav strategy, null-safe if no strategy is set
        if (nav != null && getTarget() != null) {
            return nav.getNextMove(this, map);
        }

        // no nav or no target. stay in place
        return new MoveIntention(fromTile, fromTile, this);
    }

    // dispatcher checks this to find robots that can accept tasks
    public boolean isAvailable() {
        return state == RobotState.IDLE && currentTask == null;
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
    public void update() {
        switch (state) {
            case CHARGING:
                totalChargingTicks++;
                battery = Math.min(100.0f, battery + CHARGE_PER_TICK); // cap at 100
                if (battery >= 100.0f) {
                    // fully charged — resume task or go idle
                    state = (currentTask != null) ? RobotState.MOVING : RobotState.IDLE;
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
                        WorkloadTaskRecordBuilder recordBuilder = new WorkloadTaskRecordBuilder(currentTask);
                        WorkloadTaskRecord record = recordBuilder.buildTaskCompletionRecord(currentTick);
                        Logger.logTaskEvent(TaskEvent.TASK_COMPLETED, record);
                    }
                    currentTask = null;
                    hasPickedUp = false;
                    state = RobotState.IDLE;
                }
                break;

            case MOVING:
                totalMovingTicks++;
                // check if robot actually moved this tick
                if (previousPosition != null && !getPosition().equals(previousPosition)) {
                    consumeEnergy(ENERGY_PER_MOVE);
                    totalEnergyConsumed += ENERGY_PER_MOVE;
                    totalDistanceMoved++;
                    stuckTicks = 0;
                } else {
                    stuckTicks++;
                }

                // check arrival at pickup location
                if (currentTask != null && !hasPickedUp
                        && getPosition().equals(currentTask.getPickupLocation())) {
                    state = RobotState.LOADING;
                    loadingTicksRemaining = DEFAULT_LOADING_TICKS;
                }

                // check arrival at dropoff location
                if (currentTask != null && hasPickedUp
                        && getPosition().equals(currentTask.getDropoffLocation())) {
                    state = RobotState.UNLOADING;
                    unloadingTicksRemaining = DEFAULT_UNLOADING_TICKS;
                }
                break;

            case IDLE:
                totalIdleTicks++;
                break;

            default:
                break;
        }
    }

    // readable debug output
    @Override
    public String toString() {
        return "Robot{name=" + getName() + ", pos=" + getPosition()
                + ", battery=" + battery + ", state=" + state + "}";
    }
}
