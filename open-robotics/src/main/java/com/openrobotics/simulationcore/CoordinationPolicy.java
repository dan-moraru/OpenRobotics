package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;

import java.util.ArrayList;
import java.util.List;

public interface CoordinationPolicy {
    CoordinationPolicy NO_OP = (map, intentions) -> copyNonNull(intentions);

    // Applies policy rules to this tick's intentions
    // Policy can force robots to wait by returning a WAIT intention
    MoveIntention[] apply(Map map, MoveIntention[] intentions);

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
