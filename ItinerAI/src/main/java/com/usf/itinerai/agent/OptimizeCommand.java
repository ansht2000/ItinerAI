package com.usf.itinerai.agent;

import java.util.List;

// what the traveler asked for. lockedItemIds is null when they didn't choose, which locks every reservation;
// previousChanges and feedback are only set when refining an earlier proposal
public record OptimizeCommand(String goal, List<Long> lockedItemIds, boolean allowRemovals,
                              List<ProposedChange> previousChanges, List<String> feedback) {

    public OptimizeCommand {
        lockedItemIds = lockedItemIds == null ? null : List.copyOf(lockedItemIds);
        previousChanges = previousChanges == null ? List.of() : List.copyOf(previousChanges);
        feedback = feedback == null ? List.of() : List.copyOf(feedback);
    }
}
