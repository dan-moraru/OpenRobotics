package com.openrobotics.simulationcore;

/**
 * Basic CS5 reservation-k policy.
 *
 * <p>This class now acts as immutable configuration for engine-managed reservation logic.
 * The simulation engine owns the central reservation table, path-segment planning,
 * atomic grant/deny behavior, and reservation cleanup lifecycle.</p>
 *
 * <p>The {@link #apply(MoveIntention[])} method intentionally behaves as a pass-through.
 * {@link SimulationEngine} special-cases this policy and replaces the generic coordination
 * stage with the engine-managed reservation pipeline.</p>
 */
public class ReservationKPolicy implements CoordinationPolicy {
    private final int k;

    public ReservationKPolicy(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        this.k = k;
    }

    @Override
    public MoveIntention[] apply(MoveIntention[] intentions) {
        // The engine owns Reservation_K runtime state, so the policy itself remains
        // side-effect free and simply preserves deterministic ordering if called directly.
        return CoordinationPolicy.orderedIntentions(intentions);
    }

    public int getK() {
        return this.k;
    }
}
