package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;

import java.util.ArrayList;
import java.util.List;

/** coordination policy interface; implementations filter or delay move intentions each tick */
public interface CoordinationPolicy {
    CoordinationPolicy NO_OP = (map, intentions) -> copyNonNull(intentions);

    // Applies policy rules to this tick's intentions
    // Policy can force robots to wait by returning a WAIT intention
    MoveIntention[] apply(Map map, MoveIntention[] intentions);

    // Coordination-state cleanup hook used by both reroute attempts and full fallback recovery.
    default void clearRobotCoordinationState(Robot robot) {}

    // Default policy: return a copy with null intentions removed
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
