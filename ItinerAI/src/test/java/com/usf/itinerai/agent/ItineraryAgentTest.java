package com.usf.itinerai.agent;

import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.jayway.jsonpath.JsonPath;
import com.usf.itinerai.feasibility.FeasibilityService;
import com.usf.itinerai.feasibility.OverlapConstraint;
import com.usf.itinerai.feasibility.TravelTimeConstraint;
import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.routing.RoutingException;
import com.usf.itinerai.trip.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.usf.itinerai.agent.ScriptedReplies.body;
import static com.usf.itinerai.agent.ScriptedReplies.call;
import static com.usf.itinerai.agent.ScriptedReplies.endTurn;
import static com.usf.itinerai.agent.ScriptedReplies.stoppedWith;
import static com.usf.itinerai.agent.ScriptedReplies.toolUse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// the loop against a scripted fake model, so no API key or network is needed
class ItineraryAgentTest {

    private static final Location LOUVRE =
            new Location("Louvre", 48.8606, 2.3376, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private static final Location CONSULAT = new Location("Le Consulat", 48.8866, 2.3404, null, null);
    private static final AgentProperties PROPERTIES =
            new AgentProperties("test-key", "claude-sonnet-5-5", "high", 8, Duration.ofSeconds(90), 16000);
    private static final String MOVE_LOUVRE_EARLIER = call("toolu_1", "move_item", """
            {"item_id": 1, "start_time": "09:20", "end_time": "11:20", "reason": "Leaves time to walk to lunch."}""");

    private final ManualClock clock = new ManualClock();
    private ItineraryDraft draft;

    // the Louvre (1) ends 20 minutes before lunch (2), which is a 50 minute walk away; lunch is locked
    @BeforeEach
    void setUp() {
        Trip trip = new Trip(42L, "Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"),
                TravelMode.WALK);
        trip.addItem(new Activity(1L, "Louvre", LOUVRE, LocalTime.parse("10:00"), LocalTime.parse("12:00"), null,
                "art"));
        trip.addItem(new Reservation(2L, "Lunch at Le Consulat", CONSULAT, LocalTime.parse("12:20"),
                LocalTime.parse("13:30"), null, "ABC123", 2));
        draft = new ItineraryDraft(trip, Set.of(2L), false);
    }

    @Test
    void toolCallsChangeTheDraftUntilClaudeIsDone() {
        ScriptedModel model = new ScriptedModel(toolUse(MOVE_LOUVRE_EARLIER),
                endTurn("Your lunch stays at 12:20. I moved the Louvre 40 minutes earlier."));

        ItineraryAgent.Result result = agent(model, fiftyMinutes()).run(draft, "first message");

        assertThat(result).isEqualTo(new ItineraryAgent.Result(true,
                "Your lunch stays at 12:20. I moved the Louvre 40 minutes earlier."));
        assertThat(draft.changes()).singleElement().satisfies(change -> {
            assertThat(change.after()).isEqualTo(new TimeRange(LocalTime.parse("09:20"), LocalTime.parse("11:20")));
            assertThat(change.reason()).isEqualTo("Leaves time to walk to lunch.");
        });
        // the second request is the first message, Claude's reply as it was, then the tool's result
        String second = body(model.requests.get(1));
        assertThat(JsonPath.<List<Object>>read(second, "$.messages")).hasSize(3);
        assertThat(JsonPath.<String>read(second, "$.messages[0].content")).isEqualTo("first message");
        assertThat(JsonPath.<String>read(second, "$.messages[1].role")).isEqualTo("assistant");
        assertThat(JsonPath.<String>read(second, "$.messages[1].content[0].id")).isEqualTo("toolu_1");
        assertThat(JsonPath.<String>read(second, "$.messages[2].content[0].tool_use_id")).isEqualTo("toolu_1");
        assertThat(JsonPath.<Boolean>read(second, "$.messages[2].content[0].is_error")).isFalse();
        assertThat(JsonPath.<String>read(second, "$.messages[2].content[0].content"))
                .contains("Conflicts: none. The day works.");
    }

    @Test
    void everyRequestUsesTheConfiguredModelAndSettings() {
        ScriptedModel model = new ScriptedModel(endTurn("Nothing to change."));

        agent(model, fiftyMinutes()).run(draft, "first message");

        MessageCreateParams request = model.requests.getFirst();
        String json = body(request);
        assertThat(JsonPath.<String>read(json, "$.model")).isEqualTo("claude-sonnet-5-5");
        assertThat(JsonPath.<Integer>read(json, "$.max_tokens")).isEqualTo(16000);
        assertThat(JsonPath.<String>read(json, "$.output_config.effort")).isEqualTo("high");
        assertThat(JsonPath.<String>read(json, "$.cache_control.type")).isEqualTo("ephemeral");
        assertThat(JsonPath.<String>read(json, "$.fallbacks")).isEqualTo("default");
        assertThat(JsonPath.<String>read(json, "$.system")).isEqualTo(OptimizerPrompt.SYSTEM);
        assertThat(JsonPath.<List<String>>read(json, "$.tools[*].name"))
                .containsExactly("get_travel_time", "move_item", "remove_item");
        assertThat(JsonPath.<List<Boolean>>read(json, "$.tools[*].strict")).containsOnly(true);
        assertThat(request.betas().orElseThrow()).extracting(Object::toString)
                .contains(ItineraryAgent.FALLBACK_BETA);
    }

    @Test
    void parallelToolCallsAreAnsweredTogetherInOrder() {
        ScriptedModel model = new ScriptedModel(
                toolUse(call("toolu_1", "get_travel_time", """
                                {"from_item_id": 1, "to_item_id": 2, "mode": "WALK"}"""),
                        MOVE_LOUVRE_EARLIER.replace("toolu_1", "toolu_2")),
                endTurn("Done."));

        agent(model, fiftyMinutes()).run(draft, "first message");

        String second = body(model.requests.get(1));
        assertThat(JsonPath.<List<String>>read(second, "$.messages[2].content[*].tool_use_id"))
                .containsExactly("toolu_1", "toolu_2");
        assertThat(JsonPath.<String>read(second, "$.messages[2].content[0].content"))
                .isEqualTo("About 50 min from Louvre to Le Consulat by WALK.");
    }

    @Test
    void rejectedToolCallGoesBackAsAnErrorAndTheLoopContinues() {
        ScriptedModel model = new ScriptedModel(
                toolUse(call("toolu_1", "move_item", """
                        {"item_id": 2, "start_time": "13:00", "end_time": "14:00", "reason": "Later lunch."}""")),
                toolUse(MOVE_LOUVRE_EARLIER.replace("toolu_1", "toolu_2")),
                endTurn("I kept your lunch booking and moved the Louvre instead."));

        ItineraryAgent.Result result = agent(model, fiftyMinutes()).run(draft, "first message");

        String second = body(model.requests.get(1));
        assertThat(JsonPath.<Boolean>read(second, "$.messages[2].content[0].is_error")).isTrue();
        assertThat(JsonPath.<String>read(second, "$.messages[2].content[0].content"))
                .isEqualTo("\"Lunch at Le Consulat\" is locked, so it can't change");
        assertThat(result.finished()).isTrue();
        assertThat(draft.changes()).extracting(ProposedChange::itemId).containsExactly(1L);
    }

    @Test
    void roundLimitReturnsTheDraftSoFar() {
        AgentProperties threeRounds =
                new AgentProperties("test-key", "claude-sonnet-5-5", "high", 3, Duration.ofSeconds(90), 16000);
        BetaMessage[] keepsGoing = new BetaMessage[5];
        Arrays.fill(keepsGoing, toolUse(MOVE_LOUVRE_EARLIER));
        ScriptedModel model = new ScriptedModel(keepsGoing);

        ItineraryAgent.Result result = new ItineraryAgent(model, tools(fiftyMinutes()), threeRounds, clock)
                .run(draft, "first message");

        assertThat(model.requests).hasSize(3);
        assertThat(result).isEqualTo(new ItineraryAgent.Result(false, ItineraryAgent.UNFINISHED_SUMMARY));
        // the last round's edits were still made
        assertThat(draft.changes()).hasSize(1);
    }

    @Test
    void noNewRoundStartsAfterTheTimeLimit() {
        BetaMessage[] keepsGoing = new BetaMessage[5];
        Arrays.fill(keepsGoing, toolUse(MOVE_LOUVRE_EARLIER));
        ScriptedModel model = new ScriptedModel(keepsGoing);
        // each call to Claude takes a minute, against a 90 second limit
        model.onEachCall = () -> clock.advance(Duration.ofMinutes(1));

        ItineraryAgent.Result result = agent(model, fiftyMinutes()).run(draft, "first message");

        assertThat(model.requests).hasSize(2);
        assertThat(result.finished()).isFalse();
    }

    @Test
    void refusalKeepsTheDraftSoFar() {
        ScriptedModel model = new ScriptedModel(toolUse(MOVE_LOUVRE_EARLIER), stoppedWith("refusal"));

        ItineraryAgent.Result result = agent(model, fiftyMinutes()).run(draft, "first message");

        assertThat(result.finished()).isFalse();
        assertThat(draft.changes()).hasSize(1);
    }

    @Test
    void cutOffReplyKeepsTheDraftSoFar() {
        ScriptedModel model = new ScriptedModel(stoppedWith("max_tokens"));

        ItineraryAgent.Result result = agent(model, fiftyMinutes()).run(draft, "first message");

        assertThat(result.finished()).isFalse();
        assertThat(draft.changes()).isEmpty();
    }

    @Test
    void routingFailureStopsTheRun() {
        RouteService failing = (origin, destination, mode) -> {
            throw new RoutingException("could not reach the Google Routes API");
        };
        ScriptedModel model = new ScriptedModel(toolUse(MOVE_LOUVRE_EARLIER), endTurn("Done."));

        assertThatThrownBy(() -> agent(model, failing).run(draft, "first message"))
                .isInstanceOf(RoutingException.class);
    }

    private ItineraryAgent agent(ModelClient model, RouteService routes) {
        return new ItineraryAgent(model, tools(routes), PROPERTIES, clock);
    }

    private static AgentTools tools(RouteService routes) {
        return new AgentTools(routes,
                new FeasibilityService(List.of(new OverlapConstraint(), new TravelTimeConstraint(routes))));
    }

    // 50 minutes between any two different places
    private static RouteService fiftyMinutes() {
        return (origin, destination, mode) ->
                Optional.of(origin == destination ? Duration.ZERO : Duration.ofMinutes(50));
    }

    // returns its scripted replies in order and keeps every request it was sent
    private static final class ScriptedModel implements ModelClient {

        private final List<MessageCreateParams> requests = new ArrayList<>();
        private final Deque<BetaMessage> replies;
        private Runnable onEachCall = () -> {
        };

        ScriptedModel(BetaMessage... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public BetaMessage send(MessageCreateParams params) {
            requests.add(params);
            onEachCall.run();
            if (replies.isEmpty()) {
                throw new AssertionError("the agent asked for more replies than were scripted");
            }
            return replies.removeFirst();
        }
    }

    // a clock the test moves forward by hand
    private static final class ManualClock extends Clock {

        private Instant now = Instant.parse("2026-11-11T08:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
