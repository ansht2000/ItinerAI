package com.usf.itinerai.agent;

import com.usf.itinerai.TestcontainersConfiguration;
import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.trip.Trip;
import com.usf.itinerai.trip.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// calls the real Claude API (a few cents a run), so it's skipped unless ANTHROPIC_API_KEY is set in .env or the
// environment, which CI doesn't have. Travel times are stubbed, so it needs no Google key
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ItineraryAgentLiveTest {

    private static final Location LOUVRE =
            new Location("Louvre", 48.8606, 2.3376, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private static final Location CONSULAT = new Location("Le Consulat", 48.8866, 2.3404, null, null);
    private static final Location SACRE_COEUR =
            new Location("Sacré-Cœur", 48.8867, 2.3431, LocalTime.of(6, 30), LocalTime.of(22, 30));

    @Autowired
    private AgentProperties properties;

    @Autowired
    private TripService tripService;

    @Autowired
    private OptimizationService optimizationService;

    @MockitoBean
    private RouteService routeService;

    @Test
    void fixesTheParisMorningWithoutMovingTheLunchReservation() {
        assumeTrue(properties.hasApiKey(), "set ANTHROPIC_API_KEY to run the live agent test");
        // a 50 minute walk from the Louvre to lunch, 5 minutes between any other two places
        when(routeService.travelTime(any(), any(), any())).thenAnswer(call -> {
            Location from = call.getArgument(0);
            Location to = call.getArgument(1);
            if (from.getName().equals(to.getName())) {
                return Optional.of(Duration.ZERO);
            }
            boolean louvreToLunch = from.getName().equals("Louvre") && to.getName().equals("Le Consulat");
            return Optional.of(Duration.ofMinutes(louvreToLunch ? 50 : 5));
        });
        Trip trip = new Trip("Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"),
                TravelMode.WALK);
        trip.addItem(new Activity("Louvre", LOUVRE, LocalTime.parse("10:00"), LocalTime.parse("12:00"), null, "art"));
        trip.addItem(new Reservation("Lunch at Le Consulat", CONSULAT, LocalTime.parse("12:20"),
                LocalTime.parse("13:30"), null, "ABC123", 2));
        trip.addItem(new Activity("Sacré-Cœur", SACRE_COEUR, LocalTime.parse("13:45"), LocalTime.parse("15:00"),
                null, "history"));
        Trip saved = tripService.createTrip(trip);
        long lunchId = saved.getItinerary().getItems().stream()
                .filter(item -> item instanceof Reservation)
                .map(ItineraryItem::getId)
                .findFirst().orElseThrow();

        Optimization result = optimizationService.optimize(saved.getId(),
                new OptimizeCommand(null, null, false, List.of(), List.of()));

        assertThat(result.feasible()).as(result.summary()).isTrue();
        assertThat(result.finished()).isTrue();
        assertThat(result.changes()).extracting(ProposedChange::itemId).doesNotContain(lunchId);
        assertThat(result.summary()).isNotBlank();
    }
}
