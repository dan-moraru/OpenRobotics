package com.openrobotics.robot;

/**
 * Immutable value object holding robot physics constants.
 * Constructed from ConfigSection and passed to each Robot at creation time.
 */
public class RobotConfig {
    public final float batteryCapacity;
    public final float lowBatteryThreshold;
    public final float chargePerTick;
    public final float energyPerMove;
    public final int   loadingTicks;
    public final int   unloadingTicks;

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

    /** Default config matching previous hardcoded values. */
    public static RobotConfig defaults() {
        return new RobotConfig(100.0f, 20.0f, 5.0f, 1.0f, 1, 1);
    }
}
