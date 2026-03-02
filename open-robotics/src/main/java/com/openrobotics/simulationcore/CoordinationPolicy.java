package com.openrobotics.simulationcore;

import java.util.ArrayList;
import java.util.List;

public interface CoordinationPolicy {
    // Applies policy rules to this tick's intentions
    // Policy can force robots to wait by returning a WAIT intention
    MoveIntention[] apply(MoveIntention[] intentions);

    // Default policy: do not change intentions
    static CoordinationPolicy noOp() {
        return intentions -> copyNonNull(intentions);
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
