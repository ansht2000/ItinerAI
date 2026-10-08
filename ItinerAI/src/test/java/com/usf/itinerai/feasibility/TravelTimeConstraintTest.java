package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.trip.Trip;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TravelTimeConstraintTest {

    private static final Location HOTEL = new Location("Hotel", 48.8625, 2.3360, null, null);
    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);
    private static final Location CAFE = new Location("Cafe", 48.8530, 2.3499, null, null);

    @Test
    void enoughTimeHasNoConflicts() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", LOUVRE, "10:00", "11:00"));
        trip.addItem(activity("Lunch", CAFE, "11:30", "12:30"));

        assertThat(constraintWithTravelTime(20).check(trip)).isEmpty();
    }

    @Test
    void gapEqualToTravelTimeIsEnough() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", LOUVRE, "10:00", "11:00"));
        trip.addItem(activity("Lunch", CAFE, "11:20", "12:20"));

        assertThat(constraintWithTravelTime(20).check(trip)).isEmpty();
    }

    @Test
    void tooLittleTimeIsReported() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", LOUVRE, "10:00", "11:00");
        Activity lunch = activity("Lunch", CAFE, "11:10", "12:10");
        trip.addItem(museum);
        trip.addItem(lunch);

        assertThat(constraintWithTravelTime(25).check(trip)).containsExactly(new Conflict(
                ConflictType.INSUFFICIENT_TRAVEL_TIME,
                "not enough time to get from \"Museum\" (ends 11:00) to \"Lunch\" (starts 11:10) by WALK: "
                        + "25 min needed, 10 min available, 15 min short",
                List.of(museum, lunch)));
    }

    @Test
    void backToBackItemsAtDifferentPlacesAreReported() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", LOUVRE, "10:00", "11:00");
        Activity lunch = activity("Lunch", CAFE, "11:00", "12:00");
        trip.addItem(museum);
        trip.addItem(lunch);

        assertThat(constraintWithTravelTime(15).check(trip)).containsExactly(new Conflict(
                ConflictType.INSUFFICIENT_TRAVEL_TIME,
                "not enough time to get from \"Museum\" (ends 11:00) to \"Lunch\" (starts 11:00) by WALK: "
                        + "15 min needed, 0 min available, 15 min short",
                List.of(museum, lunch)));
    }

    @Test
    void pairWithNoRouteIsSkipped() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", LOUVRE, "10:00", "11:00"));
        trip.addItem(activity("Lunch", CAFE, "11:00", "12:00"));
        TravelTimeConstraint constraint = new TravelTimeConstraint((origin, destination, mode) -> Optional.empty());

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void pairWithNoRouteDoesNotHideOtherConflicts() {
        Trip trip = parisTrip();
        Activity lunch = activity("Lunch", CAFE, "12:00", "13:00");
        Activity walk = activity("Walk", HOTEL, "13:05", "14:00");
        trip.addItem(activity("Museum", LOUVRE, "10:00", "11:00"));
        trip.addItem(lunch);
        trip.addItem(walk);
        // no route out of the Louvre, 30 minutes everywhere else
        TravelTimeConstraint constraint = new TravelTimeConstraint((origin, destination, mode) ->
                origin == LOUVRE ? Optional.empty() : Optional.of(Duration.ofMinutes(30)));

        assertThat(constraint.check(trip)).singleElement()
                .extracting(Conflict::items).isEqualTo(List.of(lunch, walk));
    }

    @Test
    void defaultTravelModeIsUsedBetweenActivities() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", LOUVRE, "10:00", "11:00"));
        trip.addItem(activity("Lunch", CAFE, "12:00", "13:00"));
        List<String> calls = new ArrayList<>();

        new TravelTimeConstraint(recording(calls)).check(trip);

        assertThat(calls).containsExactly("Louvre -> Cafe by WALK");
    }

    @Test
    void transportationModeAndOriginAreUsed() {
        Trip trip = parisTrip();
        trip.addItem(activity("Breakfast", HOTEL, "08:00", "09:00"));
        trip.addItem(new Transportation("Metro to the Louvre", LOUVRE, LocalTime.parse("09:00"),
                LocalTime.parse("09:30"), null, HOTEL, TravelMode.TRANSIT));
        trip.addItem(activity("Museum", LOUVRE, "09:30", "11:00"));
        List<String> calls = new ArrayList<>();

        new TravelTimeConstraint(recording(calls)).check(trip);

        // to the metro's origin, then from its destination
        assertThat(calls).containsExactly("Hotel -> Hotel by TRANSIT", "Louvre -> Louvre by TRANSIT");
    }

    @Test
    void overlappingItemsAreLeftToOverlapConstraint() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", LOUVRE, "10:00", "11:30"));
        trip.addItem(activity("Lunch", CAFE, "11:00", "12:00"));
        List<String> calls = new ArrayList<>();

        assertThat(new TravelTimeConstraint(recording(calls)).check(trip)).isEmpty();
        assertThat(calls).isEmpty();
    }

    @Test
    void emptyItineraryHasNoConflicts() {
        assertThat(constraintWithTravelTime(30).check(parisTrip())).isEmpty();
    }

    private TravelTimeConstraint constraintWithTravelTime(int minutes) {
        return new TravelTimeConstraint((origin, destination, mode) -> Optional.of(Duration.ofMinutes(minutes)));
    }

    // records each lookup as "origin -> destination by MODE" and reports no travel time
    private RouteService recording(List<String> calls) {
        return (origin, destination, mode) -> {
            calls.add(origin.getName() + " -> " + destination.getName() + " by " + mode);
            return Optional.of(Duration.ZERO);
        };
    }

    private Trip parisTrip() {
        return new Trip("Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"), TravelMode.WALK);
    }

    private Activity activity(String title, Location location, String start, String end) {
        return new Activity(title, location, LocalTime.parse(start), LocalTime.parse(end), null, null);
    }
}
