package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;

import java.util.ArrayList;
import java.util.List;

/** Coordination policy interface; implementations filter or delay move intentions each tick. */
public interface CoordinationPolicy {
    CoordinationPolicy NO_OP = (map, intentions) -> copyNonNull(intentions);

    /**
     * Applies policy rules to this tick's intentions.
     * Implementations may force robots to wait by substituting a stay-in-place intention.
     *
     * @param map the current warehouse map
     * @param intentions the raw move intentions for this tick
     * @return the filtered or modified intentions after policy is applied
     */
    MoveIntention[] apply(Map map, MoveIntention[] intentions);

    /**
     * Releases any per-robot coordination state held by this policy.
     * Called on both reroute attempts and full fallback deadlock recovery.
     *
     * @param robot the robot whose state should be cleared
     */
    default void clearRobotCoordinationState(Robot robot) {}

    /**
     * Returns the no-op policy; passes all non-null intentions through unchanged.
     *
     * @return the shared no-op {@link CoordinationPolicy} instance
     */
    static CoordinationPolicy noOp() {
        return NO_OP;
    }

    // helper for no-op policy
    private static MoveIntention[] copyNonNull(MoveIntention[] intentions) {
        if (intentions == null || intentions.length == 0) {
            return new MoveIntention[0];
        }

        List<MoveIntention> list = new ArrayList<>();
        for (MoveIntention intention : intentions) {
            if (intention != null) {
                list.add(intention);
            }
        }
        return list.toArray(new MoveIntention[0]);
    }
}
