package com.usf.itinerai.agent;

import com.usf.itinerai.feasibility.Conflict;
import com.usf.itinerai.feasibility.FeasibilityService;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.trip.Trip;
import com.usf.itinerai.trip.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// what the controller calls: builds a proposal with the agent, or writes an accepted one to the trip
@Service
public class OptimizationService {

    static final String NOTHING_TO_FIX =
            "This day already works and you didn't ask for anything else, so there's nothing to change.";

    private final TripService tripService;
    private final FeasibilityService feasibilityService;
    private final ItineraryAgent agent;

    public OptimizationService(TripService tripService, FeasibilityService feasibilityService, ItineraryAgent agent) {
        this.tripService = tripService;
        this.feasibilityService = feasibilityService;
        this.agent = agent;
    }

    // nothing is saved; the proposal's changes are always compared with the trip as saved
    public Optimization optimize(long tripId, OptimizeCommand command) {
        Trip saved = tripService.getTrip(tripId);
        ItineraryDraft draft = new ItineraryDraft(saved, locks(saved, command.lockedItemIds()),
                command.allowRemovals());
        draft.applyPrevious(command.previousChanges());
        List<Conflict> conflictsBefore = feasibilityService.check(saved);
        List<Conflict> startingConflicts = command.previousChanges().isEmpty()
                ? conflictsBefore : feasibilityService.check(draft.toTrip());

        ItineraryAgent.Result result;
        boolean askedForNothing = (command.goal() == null || command.goal().isBlank()) && command.feedback().isEmpty();
        if (startingConflicts.isEmpty() && askedForNothing) {
            result = new ItineraryAgent.Result(true, NOTHING_TO_FIX);
        } else {
            result = agent.run(draft, OptimizerPrompt.firstMessage(draft, startingConflicts, command.goal(),
                    command.feedback(), !command.previousChanges().isEmpty()));
        }
        // the verdict comes from the feasibility check, not from anything the model said
        List<Conflict> conflictsAfter = feasibilityService.check(draft.toTrip());
        return new Optimization(tripId, result.finished(), result.summary(), draft.changes(), conflictsBefore,
                conflictsAfter, draft.items());
    }

    // all or nothing: every change is checked against the saved trip before anything is written
    @Transactional
    public Trip apply(long tripId, List<ProposedChange> changes) {
        Trip saved = tripService.getTrip(tripId);
        Map<Long, ItineraryItem> savedItems = new LinkedHashMap<>();
        saved.getItinerary().getItems().forEach(item -> savedItems.put(item.getId(), item));
        Set<Long> seen = new HashSet<>();
        for (ProposedChange change : changes) {
            if (!seen.add(change.itemId())) {
                throw new IllegalArgumentException("item " + change.itemId() + " appears in more than one change");
            }
            change.findUnchangedIn(savedItems);
        }
        for (ProposedChange change : changes) {
            switch (change.type()) {
                case RESCHEDULE -> tripService.updateItem(tripId,
                        ItineraryDraft.withTimes(savedItems.get(change.itemId()), change.after()));
                case REMOVE -> tripService.deleteItem(tripId, change.itemId());
            }
        }
        return tripService.getTrip(tripId);
    }

    // no choice from the traveler means every reservation is locked, since moving one means rebooking it
    private static Set<Long> locks(Trip saved, List<Long> lockedItemIds) {
        if (lockedItemIds != null) {
            return Set.copyOf(lockedItemIds);
        }
        return saved.getItinerary().getItems().stream()
                .filter(item -> item instanceof Reservation)
                .map(ItineraryItem::getId)
                .collect(Collectors.toSet());
    }
}
