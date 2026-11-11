package com.usf.itinerai.web;

import com.anthropic.models.beta.messages.MessageCreateParams;
import com.jayway.jsonpath.JsonPath;
import com.usf.itinerai.TestcontainersConfiguration;
import com.usf.itinerai.agent.AgentUnavailableException;
import com.usf.itinerai.agent.ModelClient;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.routing.RoutingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

import static com.usf.itinerai.agent.ScriptedReplies.body;
import static com.usf.itinerai.agent.ScriptedReplies.call;
import static com.usf.itinerai.agent.ScriptedReplies.endTurn;
import static com.usf.itinerai.agent.ScriptedReplies.toolUse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// goes through the real HTTP layer, draft, tools, constraints and Postgres in Docker, with stand-ins for Claude
// and Google
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OptimizationControllerTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ModelClient modelClient;

    @MockitoBean
    private RouteService routeService;

    private long tripId;
    private long louvreId;
    private long lunchId;

    // the Louvre ends 20 minutes before a lunch booking that's a 50 minute walk away
    @BeforeEach
    void createTrip() throws Exception {
        tripId = idOf(mvc.perform(post("/api/trips").contentType(JSON).content("""
                        {"name": "Paris day", "destination": "Paris", "date": "2026-11-01",
                         "timeZone": "Europe/Paris", "defaultTravelMode": "WALK"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        louvreId = addItem("""
                {"type": "activity", "title": "Louvre", "startTime": "10:00", "endTime": "12:00",
                 "location": {"name": "Louvre", "latitude": 48.8606, "longitude": 2.3376,
                              "openTime": "09:00", "closeTime": "18:00"}}
                """);
        lunchId = addItem("""
                {"type": "reservation", "title": "Lunch at Le Consulat", "startTime": "12:20", "endTime": "13:30",
                 "location": {"name": "Le Consulat", "latitude": 48.8866, "longitude": 2.3404},
                 "confirmationCode": "ABC123", "partySize": 2}
                """);
        travelTakes(Duration.ofMinutes(50));
    }

    @Test
    void proposalFixesTheDayWithoutSavingIt() throws Exception {
        when(modelClient.send(any())).thenReturn(
                toolUse(moveLouvre("toolu_1", "09:20", "11:20")),
                endTurn("Your lunch stays at 12:20. I moved the Louvre 40 minutes earlier."));

        mvc.perform(post(optimize()).contentType(JSON).content("""
                        {"goal": "Make the day work"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.summary").value("Your lunch stays at 12:20. I moved the Louvre 40 minutes earlier."))
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].type").value("RESCHEDULE"))
                .andExpect(jsonPath("$.changes[0].itemId").value(louvreId))
                .andExpect(jsonPath("$.changes[0].title").value("Louvre"))
                .andExpect(jsonPath("$.changes[0].before.startTime").value("10:00:00"))
                .andExpect(jsonPath("$.changes[0].before.endTime").value("12:00:00"))
                .andExpect(jsonPath("$.changes[0].after.startTime").value("09:20:00"))
                .andExpect(jsonPath("$.changes[0].after.endTime").value("11:20:00"))
                .andExpect(jsonPath("$.changes[0].reason").value("Leaves time to walk to lunch."))
                .andExpect(jsonPath("$.changes[0].afterValid").doesNotExist())
                .andExpect(jsonPath("$.changes[0].before.rangeValid").doesNotExist())
                .andExpect(jsonPath("$.conflictsBefore[0].type").value("INSUFFICIENT_TRAVEL_TIME"))
                .andExpect(jsonPath("$.conflictsAfter").isEmpty())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].startTime").value("09:20:00"));

        // a proposal changes nothing until it's applied
        mvc.perform(get("/api/trips/{tripId}/items/{itemId}", tripId, louvreId))
                .andExpect(jsonPath("$.startTime").value("10:00:00"));
    }

    @Test
    void reservationsAreLockedUnlessTheRequestSaysOtherwise() throws Exception {
        when(modelClient.send(any())).thenReturn(
                toolUse(moveLunch("13:10", "14:20")),
                endTurn("Your lunch is booked, so I left it alone."));

        mvc.perform(post(optimize()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes").isEmpty())
                .andExpect(jsonPath("$.feasible").value(false));

        ArgumentCaptor<MessageCreateParams> requests = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(modelClient, times(2)).send(requests.capture());
        String second = body(requests.getAllValues().get(1));
        assertThat(JsonPath.<Boolean>read(second, "$.messages[2].content[0].is_error")).isTrue();
        assertThat(JsonPath.<String>read(second, "$.messages[2].content[0].content")).contains("is locked");
    }

    @Test
    void lockingNothingLetsTheAgentMoveTheReservation() throws Exception {
        when(modelClient.send(any())).thenReturn(
                toolUse(moveLunch("13:10", "14:20")),
                endTurn("I moved your lunch to 13:10."));

        mvc.perform(post(optimize()).contentType(JSON).content("""
                        {"lockedItemIds": []}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].itemId").value(lunchId))
                .andExpect(jsonPath("$.feasible").value(true));
    }

    @Test
    void dayThatAlreadyWorksDoesNotCallClaude() throws Exception {
        travelTakes(Duration.ofMinutes(10));

        mvc.perform(post(optimize()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.changes").isEmpty())
                .andExpect(jsonPath("$.summary").value(containsString("nothing to change")));
        verifyNoInteractions(modelClient);
    }

    @Test
    void refineStartsFromThePreviousProposal() throws Exception {
        when(modelClient.send(any())).thenReturn(endTurn("Lunch stays at 12:20 and the Louvre starts earlier."));

        mvc.perform(post(optimize()).contentType(JSON).content("""
                        {"goal": "Make the day work", "changes": [%s], "feedback": ["Please don't move lunch"]}
                        """.formatted(louvreChange("10:00", "12:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].after.startTime").value("09:20:00"))
                .andExpect(jsonPath("$.changes[0].reason").value("Leaves time to walk to lunch."))
                .andExpect(jsonPath("$.feasible").value(true));

        ArgumentCaptor<MessageCreateParams> requests = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(modelClient).send(requests.capture());
        String firstMessage = JsonPath.read(body(requests.getValue()), "$.messages[0].content");
        assertThat(firstMessage).contains("\"Please don't move lunch\"")
                .contains("already includes the earlier proposal's changes to the saved schedule:")
                .contains("- \"Louvre\": 10:00-12:00, now 09:20-11:20 (Leaves time to walk to lunch.)")
                .contains("09:20-11:20 activity \"Louvre\"");
    }

    @Test
    void refineFromAnOutOfDateProposalIsAConflict() throws Exception {
        mvc.perform(post(optimize()).contentType(JSON).content("""
                        {"changes": [%s], "feedback": ["Earlier, please"]}
                        """.formatted(louvreChange("08:00", "10:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("\"Louvre\" changed after the proposal was made"));
        verifyNoInteractions(modelClient);
    }

    @Test
    void applyWritesEveryChange() throws Exception {
        mvc.perform(post(apply()).contentType(JSON).content("""
                        {"changes": [%s, %s]}""".formatted(louvreChange("10:00", "12:00"), removeLunch())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Louvre"))
                .andExpect(jsonPath("$.items[0].startTime").value("09:20:00"))
                .andExpect(jsonPath("$.items[0].endTime").value("11:20:00"));
    }

    @Test
    void applyingAnOutOfDateProposalWritesNothing() throws Exception {
        mvc.perform(post(apply()).contentType(JSON).content("""
                        {"changes": [%s, %s]}""".formatted(removeLunch(), louvreChange("08:00", "10:00"))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/trips/{tripId}", tripId))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].startTime").value("10:00:00"));
    }

    @Test
    void lockingAnItemThatIsntInTheTripIsABadRequest() throws Exception {
        mvc.perform(post(optimize()).contentType(JSON).content("""
                        {"lockedItemIds": [999999]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("can't lock item 999999")));
    }

    @Test
    void goalOver500CharactersIsABadRequest() throws Exception {
        mvc.perform(post(optimize()).contentType(JSON).content("""
                        {"goal": "%s"}""".formatted("a".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.goal").exists());
    }

    @Test
    void removeWithAfterTimesIsABadRequest() throws Exception {
        mvc.perform(post(apply()).contentType(JSON).content("""
                        {"changes": [{"type": "REMOVE", "itemId": %d, "before": {"startTime": "12:20", "endTime": "13:30"},
                                      "after": {"startTime": "13:00", "endTime": "14:00"}}]}""".formatted(lunchId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['changes[0].afterValid']").exists());
    }

    @Test
    void claudeUnavailableIsServiceUnavailable() throws Exception {
        when(modelClient.send(any())).thenThrow(
                new AgentUnavailableException("the itinerary assistant isn't set up: ANTHROPIC_API_KEY is not set"));

        mvc.perform(post(optimize()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("ANTHROPIC_API_KEY")));
    }

    @Test
    void routingFailureIsBadGateway() throws Exception {
        when(routeService.travelTime(any(), any(), any()))
                .thenThrow(new RoutingException("could not reach the Google Routes API"));

        mvc.perform(post(optimize()))
                .andExpect(status().isBadGateway());
        verifyNoInteractions(modelClient);
    }

    @Test
    void unknownTripIsNotFound() throws Exception {
        mvc.perform(post("/api/trips/{tripId}/optimize", -1))
                .andExpect(status().isNotFound());
    }

    private void travelTakes(Duration duration) {
        when(routeService.travelTime(any(), any(), any())).thenReturn(Optional.of(duration));
    }

    private String moveLouvre(String id, String start, String end) {
        return call(id, "move_item", """
                {"item_id": %d, "start_time": "%s", "end_time": "%s", "reason": "Leaves time to walk to lunch."}"""
                .formatted(louvreId, start, end));
    }

    private String moveLunch(String start, String end) {
        return call("toolu_1", "move_item", """
                {"item_id": %d, "start_time": "%s", "end_time": "%s", "reason": "Later lunch."}"""
                .formatted(lunchId, start, end));
    }

    // the Louvre moved to 09:20-11:20, from the given "before" times
    private String louvreChange(String beforeStart, String beforeEnd) {
        return """
                {"type": "RESCHEDULE", "itemId": %d, "title": "Louvre",
                 "before": {"startTime": "%s", "endTime": "%s"}, "after": {"startTime": "09:20", "endTime": "11:20"},
                 "reason": "Leaves time to walk to lunch."}""".formatted(louvreId, beforeStart, beforeEnd);
    }

    private String removeLunch() {
        return """
                {"type": "REMOVE", "itemId": %d, "title": "Lunch at Le Consulat",
                 "before": {"startTime": "12:20", "endTime": "13:30"}, "after": null, "reason": "Skipped."}"""
                .formatted(lunchId);
    }

    private long addItem(String json) throws Exception {
        return idOf(mvc.perform(post("/api/trips/{tripId}/items", tripId).contentType(JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String optimize() {
        return "/api/trips/" + tripId + "/optimize";
    }

    private String apply() {
        return "/api/trips/" + tripId + "/optimize/apply";
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
