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

class OverlapConstraintTest {

    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);

    private final OverlapConstraint constraint = new OverlapConstraint();

    @Test
    void separateItemsHaveNoConflicts() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", "10:00", "11:00"));
        trip.addItem(activity("Lunch", "12:00", "13:00"));

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void overlappingItemsAreReported() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", "10:00", "11:30");
        Activity lunch = activity("Lunch", "11:00", "12:00");
        trip.addItem(museum);
        trip.addItem(lunch);

        assertThat(constraint.check(trip)).containsExactly(new Conflict(
                "\"Museum\" (10:00-11:30) overlaps \"Lunch\" (11:00-12:00)", List.of(museum, lunch)));
    }

    @Test
    void backToBackItemsDoNotOverlap() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", "10:00", "11:00"));
        trip.addItem(activity("Lunch", "11:00", "12:00"));

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void emptyItineraryHasNoConflicts() {
        assertThat(constraint.check(parisTrip())).isEmpty();
    }

    private Trip parisTrip() {
        return new Trip("Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"), TravelMode.WALK);
    }

    private Activity activity(String title, String start, String end) {
        return new Activity(title, LOUVRE, LocalTime.parse(start), LocalTime.parse(end), null, null);
    }
}
