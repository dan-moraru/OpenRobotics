package com.openrobotics.robot;

/** Immutable value object holding robot physics constants; passed to each Robot at creation time. */
public class RobotConfig {
    public final float batteryCapacity;
    public final float lowBatteryThreshold;
    public final float chargePerTick;
    public final float energyPerMove;
    public final int   loadingTicks;
    public final int   unloadingTicks;

    /**
     * Creates a configuration with explicit physics values.
     *
     * @param batteryCapacity the maximum battery level
     * @param lowBatteryThreshold the battery level below which the robot seeks a charger
     * @param chargePerTick the battery units restored each tick while charging
     * @param energyPerMove the battery units consumed per tile moved
     * @param loadingTicks the number of ticks spent picking up a box
     * @param unloadingTicks the number of ticks spent dropping off a box
     */
    public RobotConfig(float batteryCapacity, float lowBatteryThreshold,
                       float chargePerTick, float energyPerMove,
                       int loadingTicks, int unloadingTicks) {
        this.batteryCapacity      = batteryCapacity;
        this.lowBatteryThreshold  = lowBatteryThreshold;
        this.chargePerTick        = chargePerTick;
        this.energyPerMove        = energyPerMove;
        this.loadingTicks         = loadingTicks;
        this.unloadingTicks       = unloadingTicks;
    }

    /**
     * Returns the default configuration values matching the simulator's original hardcoded constants.
     *
     * @return a {@code RobotConfig} with standard physics values
     */
    public static RobotConfig defaults() {
        return new RobotConfig(100.0f, 20.0f, 5.0f, 1.0f, 1, 1);
    }
}
