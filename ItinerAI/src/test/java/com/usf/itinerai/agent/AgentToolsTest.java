package com.usf.itinerai.agent;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaTool;
import com.usf.itinerai.feasibility.FeasibilityService;
import com.usf.itinerai.feasibility.OverlapConstraint;
import com.usf.itinerai.feasibility.TravelTimeConstraint;
import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.routing.RoutingException;
import com.usf.itinerai.trip.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolsTest {

    private static final Location HOTEL = new Location("Hotel", 48.8625, 2.3360, null, null);
    private static final Location LOUVRE =
            new Location("Louvre", 48.8606, 2.3376, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private static final Location CONSULAT = new Location("Le Consulat", 48.8866, 2.3404, null, null);

    private final List<String> lookups = new ArrayList<>();
    private Trip trip;

    @BeforeEach
    void setUp() {
        trip = new Trip(42L, "Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"),
                TravelMode.WALK);
        trip.addItem(new Transportation(1L, "Metro to the Louvre", LOUVRE, LocalTime.parse("09:00"),
                LocalTime.parse("09:30"), null, HOTEL, TravelMode.TRANSIT));
        trip.addItem(new Activity(2L, "Louvre", LOUVRE, LocalTime.parse("10:00"), LocalTime.parse("12:00"), null,
                "art"));
        trip.addItem(new Reservation(3L, "Lunch", CONSULAT, LocalTime.parse("12:20"), LocalTime.parse("13:30"), null,
                "ABC123", 2));
    }

    @Test
    void threeStrictTools() {
        List<BetaTool> definitions = tools(fiftyMinutes()).definitions();

        assertThat(definitions).extracting(BetaTool::name)
                .containsExactly("get_travel_time", "move_item", "remove_item");
        assertThat(definitions).allSatisfy(tool -> assertThat(tool.strict()).contains(true));
    }

    @Test
    void moveReturnsTheUpdatedDayAndItsConflicts() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        AgentTools.ToolResult result = tools(fiftyMinutes()).run("move_item", input(Map.of("item_id", 2,
                "start_time", "09:30", "end_time", "11:30", "reason", "Leaves time to walk to lunch.")), draft);

        assertThat(result.isError()).isFalse();
        assertThat(result.content())
                .contains("[2] 09:30-11:30 activity \"Louvre\" at Louvre (open 09:00-18:00)")
                .contains("Conflicts: none. The day works.");
        assertThat(draft.changes()).singleElement().extracting(ProposedChange::reason)
                .isEqualTo("Leaves time to walk to lunch.");
    }

    @Test
    void moveReportsTheConflictsThatAreLeft() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        AgentTools.ToolResult result = tools(fiftyMinutes()).run("move_item", input(Map.of("item_id", 2,
                "start_time", "10:30", "end_time", "12:00", "reason", "A shorter visit.")), draft);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Conflicts (1):").contains("- INSUFFICIENT_TRAVEL_TIME [2, 3]:");
    }

    @Test
    void lockedItemComesBackAsAnError() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(3L), false);

        AgentTools.ToolResult result = tools(fiftyMinutes()).run("move_item", input(Map.of("item_id", 3,
                "start_time", "13:00", "end_time", "14:00", "reason", "Later lunch.")), draft);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isEqualTo("\"Lunch\" is locked, so it can't change");
        assertThat(draft.changes()).isEmpty();
    }

    @Test
    void badTimeComesBackAsAnError() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        AgentTools.ToolResult result = tools(fiftyMinutes()).run("move_item", input(Map.of("item_id", 2,
                "start_time", "9:30am", "end_time", "11:30", "reason", "Earlier.")), draft);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isEqualTo("start_time must be a 24-hour time like 09:30, not \"9:30am\"");
    }

    @Test
    void removalWithoutPermissionComesBackAsAnError() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        AgentTools.ToolResult result = tools(fiftyMinutes()).run("remove_item",
                input(Map.of("item_id", 2, "reason", "Too much.")), draft);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isEqualTo("the traveler didn't allow removing items");
    }

    @Test
    void removalWithPermissionReturnsTheUpdatedDay() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), true);

        AgentTools.ToolResult result = tools(fiftyMinutes()).run("remove_item",
                input(Map.of("item_id", 2, "reason", "Too much.")), draft);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).doesNotContain("\"Louvre\"");
        assertThat(draft.changes()).singleElement().extracting(ProposedChange::type).isEqualTo(ChangeType.REMOVE);
    }

    @Test
    void travelTimeGoesFromWhereOneItemEndsToWhereTheNextStarts() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        // from the Louvre to the metro, which starts at the hotel
        AgentTools.ToolResult result = tools(fiftyMinutes()).run("get_travel_time",
                input(Map.of("from_item_id", 2, "to_item_id", 1, "mode", "WALK")), draft);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("About 50 min from Louvre to Hotel by WALK.");
        assertThat(lookups).containsExactly("Louvre -> Hotel by WALK");
    }

    @Test
    void missingRouteIsSaidPlainly() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);
        RouteService noRoutes = (origin, destination, mode) -> Optional.empty();

        AgentTools.ToolResult result = tools(noRoutes).run("get_travel_time",
                input(Map.of("from_item_id", 2, "to_item_id", 3, "mode", "BICYCLE")), draft);

        assertThat(result.content()).isEqualTo("There is no BICYCLE route from Louvre to Le Consulat.");
    }

    @Test
    void unknownToolComesBackAsAnError() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        AgentTools.ToolResult result = tools(fiftyMinutes()).run("book_taxi", input(Map.of()), draft);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).isEqualTo("there is no tool called book_taxi");
    }

    @Test
    void routingFailureIsPassedOn() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);
        RouteService failing = (origin, destination, mode) -> {
            throw new RoutingException("could not reach the Google Routes API");
        };

        assertThatThrownBy(() -> tools(failing).run("move_item", input(Map.of("item_id", 2,
                "start_time", "09:30", "end_time", "11:30", "reason", "Earlier.")), draft))
                .isInstanceOf(RoutingException.class);
    }

    // 50 minutes between any two different places; records each lookup as "origin -> destination by MODE"
    private RouteService fiftyMinutes() {
        return (origin, destination, mode) -> {
            lookups.add(origin.getName() + " -> " + destination.getName() + " by " + mode);
            return Optional.of(origin == destination ? Duration.ZERO : Duration.ofMinutes(50));
        };
    }

    private static AgentTools tools(RouteService routes) {
        return new AgentTools(routes,
                new FeasibilityService(List.of(new OverlapConstraint(), new TravelTimeConstraint(routes))));
    }

    private static JsonValue input(Map<String, Object> values) {
        return JsonValue.from(values);
    }
}
