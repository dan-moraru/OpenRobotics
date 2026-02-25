package com.openrobotics.robot;

import com.openrobotics.map.MapEntity;
import com.openrobotics.task.Task;
import com.openrobotics.map.Vector2D;

// robot entity — extends mapentity with robot-specific state (uml 3.3.4)
// inherits uuid, name, position, update() hook
public class Robot extends MapEntity {
    private float battery;
    private NavigationStrategy nav;
    private RobotState state;
    private Task currentTask;
    private int stuckTicks;

    // takes Vector2D position, delegates to MapEntity via super()
    public Robot(String name, Vector2D position) {
        super(name, position);
        this.battery = 100.0f;
        this.nav = null;
        this.state = RobotState.IDLE;
        this.currentTask = null;
        this.stuckTicks = 0;
    }

    // getters for all fields
    public float getBattery() { return battery; }
    public NavigationStrategy getNav() { return nav; }
    public RobotState getState() { return state; }
    public Task getCurrentTask() { return currentTask; }
    public int getStuckTicks() { return stuckTicks; }

    // setters for mutable robot state
    public void setBattery(float battery) { this.battery = battery; }
    public void setNav(NavigationStrategy nav) { this.nav = nav; }
    public void setState(RobotState state) { this.state = state; }
    public void setCurrentTask(Task currentTask) { this.currentTask = currentTask; }
    public void setStuckTicks(int stuckTicks) { this.stuckTicks = stuckTicks; }

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

    // per-tick update hook from mapentity — sim engine will call this
    @Override
    public void update() {
        // per-tick update logic will be added as the simulation engine develops
    }

    // readable debug output
    @Override
    public String toString() {
        return "Robot{name=" + getName() + ", pos=" + getPosition()
                + ", battery=" + battery + ", state=" + state + "}";
    }
}
