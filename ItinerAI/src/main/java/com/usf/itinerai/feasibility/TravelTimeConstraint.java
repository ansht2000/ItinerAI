package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.trip.Trip;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// flags consecutive items without enough time to travel between them
@Component
public class TravelTimeConstraint implements Constraint {

    private final RouteService routeService;

    public TravelTimeConstraint(RouteService routeService) {
        if (routeService == null) {
            throw new IllegalArgumentException("routeService must not be null");
        }
        this.routeService = routeService;
    }

    // a RoutingException from the route service is passed on, since the check can't be completed
    @Override
    public List<Conflict> check(Trip trip) {
        List<ItineraryItem> items = trip.getItinerary().getItems();
        List<Conflict> conflicts = new ArrayList<>();
        for (int i = 0; i + 1 < items.size(); i++) {
            ItineraryItem first = items.get(i);
            ItineraryItem second = items.get(i + 1);
            // overlapping items are OverlapConstraint's job
            if (second.getStartTime().isBefore(first.getEndTime())) {
                continue;
            }
            TravelMode mode = modeBetween(first, second, trip.getDefaultTravelMode());
            Optional<Duration> travelTime = routeService.travelTime(first.getLocation(), startOf(second), mode);
            // no route for this mode means the pair can't be checked, not that it's a conflict
            if (travelTime.isEmpty()) {
                continue;
            }
            Duration gap = Duration.between(first.getEndTime(), second.getStartTime());
            if (gap.compareTo(travelTime.get()) < 0) {
                conflicts.add(new Conflict(ConflictType.INSUFFICIENT_TRAVEL_TIME,
                        describe(first, second, mode, travelTime.get(), gap), List.of(first, second)));
            }
        }
        return conflicts;
    }

    // a transportation item starts at its origin; its location is where it ends up
    private static Location startOf(ItineraryItem item) {
        return item instanceof Transportation transportation ? transportation.getOrigin() : item.getLocation();
    }

    // when both are transportation, the second's mode wins, since that's the one being travelled towards
    private static TravelMode modeBetween(ItineraryItem first, ItineraryItem second, TravelMode defaultMode) {
        if (second instanceof Transportation transportation) {
            return transportation.getMode();
        }
        if (first instanceof Transportation transportation) {
            return transportation.getMode();
        }
        return defaultMode;
    }

    private static String describe(ItineraryItem first, ItineraryItem second, TravelMode mode, Duration travelTime,
                                   Duration gap) {
        return "not enough time to get from \"" + first.getTitle() + "\" (ends " + first.getEndTime() + ") to \""
                + second.getTitle() + "\" (starts " + second.getStartTime() + ") by " + mode + ": "
                + minutes(travelTime) + " min needed, " + minutes(gap) + " min available, "
                + minutes(travelTime.minus(gap)) + " min short";
    }

    // rounded up, so a shortfall of a few seconds still reads as 1 min rather than 0
    private static long minutes(Duration duration) {
        long minutes = duration.toMinutes();
        return duration.equals(Duration.ofMinutes(minutes)) ? minutes : minutes + 1;
    }
}
