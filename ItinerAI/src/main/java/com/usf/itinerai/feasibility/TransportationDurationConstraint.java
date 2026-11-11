package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.trip.Trip;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// flags transportation legs scheduled for less time than the trip takes by their own mode;
// TravelTimeConstraint checks the gaps between items, but never a leg's own length
@Component
public class TransportationDurationConstraint implements Constraint {

    private final RouteService routeService;

    public TransportationDurationConstraint(RouteService routeService) {
        if (routeService == null) {
            throw new IllegalArgumentException("routeService must not be null");
        }
        this.routeService = routeService;
    }

    // a RoutingException from the route service is passed on, since the check can't be completed
    @Override
    public List<Conflict> check(Trip trip) {
        List<Conflict> conflicts = new ArrayList<>();
        for (ItineraryItem item : trip.getItinerary().getItems()) {
            if (!(item instanceof Transportation leg)) {
                continue;
            }
            Optional<Duration> travelTime = routeService.travelTime(leg.getOrigin(), leg.getLocation(), leg.getMode());
            // no route for this mode means the leg can't be checked, not that it's a conflict
            if (travelTime.isEmpty()) {
                continue;
            }
            Duration scheduled = Duration.between(leg.getStartTime(), leg.getEndTime());
            if (scheduled.compareTo(travelTime.get()) < 0) {
                conflicts.add(new Conflict(ConflictType.INSUFFICIENT_TRAVEL_TIME,
                        describe(leg, travelTime.get(), scheduled), List.of(leg)));
            }
        }
        return conflicts;
    }

    private static String describe(Transportation leg, Duration travelTime, Duration scheduled) {
        return "not enough time for \"" + leg.getTitle() + "\" (" + leg.getStartTime() + "-" + leg.getEndTime()
                + ") to get from " + leg.getOrigin().getName() + " to " + leg.getLocation().getName() + " by "
                + leg.getMode() + ": " + minutes(travelTime) + " min needed, " + minutes(scheduled)
                + " min scheduled, " + minutes(travelTime.minus(scheduled)) + " min short";
    }

    // rounded up the same way as TravelTimeConstraint, so a shortfall of a few seconds still reads as 1 min
    private static long minutes(Duration duration) {
        long minutes = duration.toMinutes();
        return duration.equals(Duration.ofMinutes(minutes)) ? minutes : minutes + 1;
    }
}
