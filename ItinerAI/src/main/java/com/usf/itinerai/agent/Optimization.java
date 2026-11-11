package com.usf.itinerai.agent;

import com.usf.itinerai.feasibility.Conflict;
import com.usf.itinerai.itinerary.ItineraryItem;

import java.util.List;

// a proposal: what Apply would change, the conflicts before and after, and the whole proposed day.
// Nothing here is saved
public record Optimization(long tripId, boolean finished, String summary, List<ProposedChange> changes,
                           List<Conflict> conflictsBefore, List<Conflict> conflictsAfter, List<ItineraryItem> items) {

    public Optimization {
        changes = List.copyOf(changes);
        conflictsBefore = List.copyOf(conflictsBefore);
        conflictsAfter = List.copyOf(conflictsAfter);
        items = List.copyOf(items);
    }

    // decided by the feasibility check on the proposed day, never by the model
    public boolean feasible() {
        return conflictsAfter.isEmpty();
    }
}
