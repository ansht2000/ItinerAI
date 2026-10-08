package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.trip.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeasibilityServiceTest {

    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);

    @Test
    void combinesEveryConstraintsConflictsWithOverlapsFirst() {
        Trip trip = parisTrip();
        Activity museum = new Activity("Museum", LOUVRE, LocalTime.of(10, 0), LocalTime.of(11, 0), null, null);
        Activity lunch = new Activity("Lunch", LOUVRE, LocalTime.of(11, 0), LocalTime.of(12, 0), null, null);
        Conflict tooFar = new Conflict(ConflictType.INSUFFICIENT_TRAVEL_TIME, "too far", List.of(museum, lunch));
        Conflict overlap = new Conflict(ConflictType.OVERLAP, "overlap", List.of(museum, lunch));
        Constraint findsTooFar = checked -> List.of(tooFar);
        Constraint findsOverlap = checked -> List.of(overlap);

        // given in the opposite order, to show the result is ordered by type
        FeasibilityService service = new FeasibilityService(List.of(findsTooFar, findsOverlap));

        assertThat(service.check(trip)).containsExactly(overlap, tooFar);
    }

    @Test
    void tripIsFeasibleWhenNoConstraintFindsAnything() {
        Constraint findsNothing = checked -> List.of();

        FeasibilityService service = new FeasibilityService(List.of(findsNothing, findsNothing));

        assertThat(service.check(parisTrip())).isEmpty();
    }

    private Trip parisTrip() {
        return new Trip("Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"), TravelMode.WALK);
    }
}
