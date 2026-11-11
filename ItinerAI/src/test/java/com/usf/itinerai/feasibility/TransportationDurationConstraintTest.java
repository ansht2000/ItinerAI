package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.routing.RoutingException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransportationDurationConstraintTest {

    private static final Location HOTEL = new Location("Hotel", 48.8625, 2.3360, null, null);
    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);
    private static final Location CAFE = new Location("Cafe", 48.8530, 2.3499, null, null);

    @Test
    void legLongerThanTheRouteHasNoConflicts() {
        Trip trip = parisTrip();
        trip.addItem(metro("09:00", "09:30"));

        assertThat(constraintWithTravelTime(Duration.ofMinutes(20)).check(trip)).isEmpty();
    }

    @Test
    void legExactlyAsLongAsTheRouteIsEnough() {
        Trip trip = parisTrip();
        trip.addItem(metro("09:00", "09:20"));

        assertThat(constraintWithTravelTime(Duration.ofMinutes(20)).check(trip)).isEmpty();
    }

    @Test
    void legShorterThanTheRouteIsReported() {
        Trip trip = parisTrip();
        Transportation metro = metro("09:00", "09:05");
        trip.addItem(metro);

        assertThat(constraintWithTravelTime(Duration.ofMinutes(32)).check(trip)).containsExactly(new Conflict(
                ConflictType.INSUFFICIENT_TRAVEL_TIME,
                "not enough time for \"Metro to the Louvre\" (09:00-09:05) to get from Hotel to Louvre by TRANSIT: "
                        + "32 min needed, 5 min scheduled, 27 min short",
                List.of(metro)));
    }

    @Test
    void shortfallOfSecondsRoundsUpToAMinute() {
        Trip trip = parisTrip();
        trip.addItem(metro("09:00", "09:20"));

        assertThat(constraintWithTravelTime(Duration.ofMinutes(20).plusSeconds(30)).check(trip)).singleElement()
                .extracting(Conflict::message).asString()
                .endsWith("21 min needed, 20 min scheduled, 1 min short");
    }

    @Test
    void legsOwnOriginDestinationAndModeAreUsed() {
        Trip trip = parisTrip();
        trip.addItem(metro("09:00", "09:30"));
        List<String> calls = new ArrayList<>();

        new TransportationDurationConstraint(recording(calls)).check(trip);

        assertThat(calls).containsExactly("Hotel -> Louvre by TRANSIT");
    }

    @Test
    void activitiesAndReservationsAreNotChecked() {
        Trip trip = parisTrip();
        trip.addItem(new Activity("Museum", LOUVRE, LocalTime.parse("10:00"), LocalTime.parse("11:00"), null, null));
        trip.addItem(new Reservation("Lunch", CAFE, LocalTime.parse("12:00"), LocalTime.parse("13:00"), null,
                "ABC123", 2));
        List<String> calls = new ArrayList<>();

        assertThat(new TransportationDurationConstraint(recording(calls)).check(trip)).isEmpty();
        assertThat(calls).isEmpty();
    }

    @Test
    void legWithNoRouteIsSkipped() {
        Trip trip = parisTrip();
        trip.addItem(metro("09:00", "09:05"));
        TransportationDurationConstraint constraint =
                new TransportationDurationConstraint((origin, destination, mode) -> Optional.empty());

        assertThat(constraint.check(trip)).isEmpty();
    }

    @Test
    void eachShortLegIsReported() {
        Trip trip = parisTrip();
        Transportation there = metro("09:00", "09:05");
        Transportation back = new Transportation("Metro back to the hotel", HOTEL, LocalTime.parse("12:00"),
                LocalTime.parse("12:05"), null, LOUVRE, TravelMode.TRANSIT);
        trip.addItem(there);
        trip.addItem(back);

        assertThat(constraintWithTravelTime(Duration.ofMinutes(15)).check(trip)).extracting(Conflict::items)
                .containsExactly(List.of(there), List.of(back));
    }

    @Test
    void routingFailureIsPassedOn() {
        Trip trip = parisTrip();
        trip.addItem(metro("09:00", "09:30"));
        TransportationDurationConstraint constraint = new TransportationDurationConstraint((origin, destination, mode) -> {
            throw new RoutingException("could not reach the Google Routes API");
        });

        assertThatThrownBy(() -> constraint.check(trip)).isInstanceOf(RoutingException.class);
    }

    @Test
    void routeServiceIsRequired() {
        assertThatThrownBy(() -> new TransportationDurationConstraint(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyItineraryHasNoConflicts() {
        assertThat(constraintWithTravelTime(Duration.ofMinutes(30)).check(parisTrip())).isEmpty();
    }

    private TransportationDurationConstraint constraintWithTravelTime(Duration travelTime) {
        return new TransportationDurationConstraint((origin, destination, mode) -> Optional.of(travelTime));
    }

    // records each lookup as "origin -> destination by MODE" and reports no travel time
    private RouteService recording(List<String> calls) {
        return (origin, destination, mode) -> {
            calls.add(origin.getName() + " -> " + destination.getName() + " by " + mode);
            return Optional.of(Duration.ZERO);
        };
    }

    // from the hotel to the Louvre by transit
    private Transportation metro(String start, String end) {
        return new Transportation("Metro to the Louvre", LOUVRE, LocalTime.parse(start), LocalTime.parse(end), null,
                HOTEL, TravelMode.TRANSIT);
    }

    private Trip parisTrip() {
        return new Trip("Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"), TravelMode.WALK);
    }
}
