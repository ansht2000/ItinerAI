package com.usf.itinerai.agent;

import com.usf.itinerai.feasibility.Conflict;
import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.trip.Trip;

import java.util.List;
import java.util.stream.Collectors;

// what Claude is told: a fixed system prompt, then a first message describing this trip and request
final class OptimizerPrompt {

    // fixed text, so it stays cacheable across requests
    static final String SYSTEM = """
            You improve a one-day travel itinerary so that it works, changing as little as possible.
            - Change the schedule only through the tools. The day works only when the latest tool result lists no conflicts.
            - Locked items must not change. Remove items only if removals are allowed. Never shorten an item below half its original length.
            - Prefer shifting an item over shortening it, and shortening over removing it.
            - Follow the traveler's goal and all of their feedback.
            - When the day works and the goal is met, or nothing more can be done, tell the traveler in 2-3 sentences what you changed and why. Use item titles, not ids.
            """;

    private OptimizerPrompt() {
    }

    static String firstMessage(ItineraryDraft draft, List<Conflict> conflicts, String goal, List<String> feedback,
                               boolean refining) {
        Trip trip = draft.saved();
        StringBuilder text = new StringBuilder();
        text.append("Trip: \"").append(trip.getName()).append("\" in ").append(trip.getDestination())
                .append(" on ").append(trip.getDate()).append(". Times are local (").append(trip.getTimeZone())
                .append("), 24-hour, all on that day.\n");
        text.append("Between stops the traveler goes by ").append(trip.getDefaultTravelMode())
                .append(" unless a transportation item covers the trip.\n");
        text.append("Removing items: ").append(draft.removalsAllowed() ? "allowed" : "not allowed").append(".\n\n");
        text.append("Traveler's goal: ")
                .append(goal == null || goal.isBlank() ? "none given; make the day work." : quoted(goal))
                .append("\n");
        if (!feedback.isEmpty()) {
            text.append("Traveler's feedback on earlier proposals, oldest first:\n");
            feedback.forEach(message -> text.append("- ").append(quoted(message)).append("\n"));
        }
        if (refining) {
            text.append("The schedule below already includes the earlier proposal's changes.\n");
        }
        text.append("\n").append(state(draft, conflicts));
        return text.toString();
    }

    // the schedule and its conflicts: shown up front and returned after every edit
    static String state(ItineraryDraft draft, List<Conflict> conflicts) {
        StringBuilder text = new StringBuilder("Schedule:\n");
        for (ItineraryItem item : draft.items()) {
            text.append(line(item, draft.isLocked(item.getId()))).append("\n");
        }
        if (conflicts.isEmpty()) {
            text.append("\nConflicts: none. The day works.\n");
        } else {
            text.append("\nConflicts (").append(conflicts.size()).append("):\n");
            for (Conflict conflict : conflicts) {
                String ids = conflict.items().stream().map(item -> String.valueOf(item.getId()))
                        .collect(Collectors.joining(", ", "[", "]"));
                text.append("- ").append(conflict.type()).append(" ").append(ids).append(": ")
                        .append(conflict.message()).append("\n");
            }
        }
        return text.toString();
    }

    private static String line(ItineraryItem item, boolean locked) {
        String times = "[" + item.getId() + "] " + item.getStartTime() + "-" + item.getEndTime() + " ";
        String what = switch (item) {
            case Transportation leg -> "transportation \"" + leg.getTitle() + "\" from " + leg.getOrigin().getName()
                    + " to " + leg.getLocation().getName() + " by " + leg.getMode();
            case Reservation reservation -> "reservation \"" + reservation.getTitle() + "\" at "
                    + place(reservation.getLocation());
            case Activity activity -> "activity \"" + activity.getTitle() + "\" at " + place(activity.getLocation());
            default -> "\"" + item.getTitle() + "\" at " + place(item.getLocation());
        };
        return times + what + (locked ? " (locked)" : "");
    }

    private static String place(Location location) {
        if (location.getOpenTime() == null || location.getCloseTime() == null) {
            return location.getName();
        }
        return location.getName() + " (open " + location.getOpenTime() + "-" + location.getCloseTime() + ")";
    }

    private static String quoted(String text) {
        return "\"" + text.strip() + "\"";
    }
}
