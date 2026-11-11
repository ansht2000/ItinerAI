package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.trip.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatingHoursConstraintTest {

    private static final Location LOUVRE =
            new Location("Louvre", 48.8606, 2.3376, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private static final Location BISTRO =
            new Location("Bistro", 48.8530, 2.3499, LocalTime.of(12, 0), LocalTime.of(14, 30));
    private static final Location PARK = new Location("Park", 48.8462, 2.3372, null, null);
    private static final Location HOTEL = new Location("Hotel", 48.8625, 2.3360, null, null);

    private final OperatingHoursConstraint constraint = new OperatingHoursConstraint();

    @Test
    void visitWithinOpeningHoursHasNoConflicts() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", LOUVRE, "10:00", "12:00"));

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void visitFromOpeningToClosingIsFine() {
        Trip trip = parisTrip();
        trip.addItem(activity("Museum", LOUVRE, "09:00", "18:00"));

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void startingBeforeOpeningIsReported() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", LOUVRE, "08:30", "10:30");
        trip.addItem(museum);

        assertThat(constraint.check(trip)).containsExactly(new Conflict(ConflictType.OUTSIDE_OPERATING_HOURS,
                "\"Museum\" (08:30-10:30) starts before Louvre opens (open 09:00-18:00)", List.of(museum)));
    }

    @Test
    void endingAfterClosingIsReported() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", LOUVRE, "17:00", "19:00");
        trip.addItem(museum);

        assertThat(constraint.check(trip)).containsExactly(new Conflict(ConflictType.OUTSIDE_OPERATING_HOURS,
                "\"Museum\" (17:00-19:00) ends after Louvre closes (open 09:00-18:00)", List.of(museum)));
    }

    @Test
    void startingEarlyAndEndingLateIsReportedOnce() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", LOUVRE, "08:00", "19:00");
        trip.addItem(museum);

        assertThat(constraint.check(trip)).containsExactly(new Conflict(ConflictType.OUTSIDE_OPERATING_HOURS,
                "\"Museum\" (08:00-19:00) starts before Louvre opens and ends after it closes (open 09:00-18:00)",
                List.of(museum)));
    }

    @Test
    void visitWhileClosedIsReported() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", LOUVRE, "19:00", "20:00");
        trip.addItem(museum);

        assertThat(constraint.check(trip)).containsExactly(new Conflict(ConflictType.OUTSIDE_OPERATING_HOURS,
                "\"Museum\" (19:00-20:00) is scheduled while Louvre is closed (open 09:00-18:00)", List.of(museum)));
    }

    @Test
    void visitEndingRightAtOpeningIsScheduledWhileClosed() {
        Trip trip = parisTrip();
        Activity museum = activity("Museum", LOUVRE, "07:00", "09:00");
        trip.addItem(museum);

        assertThat(constraint.check(trip)).singleElement()
                .extracting(Conflict::message)
                .isEqualTo("\"Museum\" (07:00-09:00) is scheduled while Louvre is closed (open 09:00-18:00)");
    }

    @Test
    void placeWithoutHoursIsAlwaysOpen() {
        Location cafeWithOnlyAnOpeningTime = new Location("Cafe", 48.8600, 2.3400, LocalTime.of(8, 0), null);
        Trip trip = parisTrip();
        trip.addItem(activity("Sunrise walk", PARK, "05:00", "06:00"));
        trip.addItem(activity("Coffee", cafeWithOnlyAnOpeningTime, "06:30", "07:00"));

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void reservationsAreChecked() {
        Trip trip = parisTrip();
        Reservation lunch = new Reservation("Lunch", BISTRO, LocalTime.parse("14:00"), LocalTime.parse("15:00"), null,
                "ABC123", 2);
        trip.addItem(lunch);

        assertThat(constraint.check(trip)).containsExactly(new Conflict(ConflictType.OUTSIDE_OPERATING_HOURS,
                "\"Lunch\" (14:00-15:00) ends after Bistro closes (open 12:00-14:30)", List.of(lunch)));
    }

    @Test
    void transportationIsNotChecked() {
        Trip trip = parisTrip();
        // arrives at the Louvre before it opens, which is fine for the trip there
        trip.addItem(new Transportation("Metro to the Louvre", LOUVRE, LocalTime.parse("08:30"),
                LocalTime.parse("08:55"), null, HOTEL, TravelMode.TRANSIT));

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void eachItemOutsideItsHoursIsReported() {
        Trip trip = parisTrip();
        Activity earlyMuseum = activity("Museum", LOUVRE, "08:00", "10:00");
        Activity fine = activity("Park", PARK, "10:30", "11:30");
        Activity lateLunch = activity("Lunch", BISTRO, "14:00", "15:00");
        trip.addItem(earlyMuseum);
        trip.addItem(fine);
        trip.addItem(lateLunch);

        assertThat(constraint.check(trip)).extracting(Conflict::items)
                .containsExactly(List.of(earlyMuseum), List.of(lateLunch));
    }

    @Test
    void emptyItineraryHasNoConflicts() {
        assertThat(constraint.check(parisTrip())).isEmpty();
    }

    private Trip parisTrip() {
        return new Trip("Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"), TravelMode.WALK);
    }

    private Activity activity(String title, Location location, String start, String end) {
        return new Activity(title, location, LocalTime.parse(start), LocalTime.parse(end), null, null);
    }
}
