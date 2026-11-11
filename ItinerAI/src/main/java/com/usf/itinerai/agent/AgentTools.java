package com.usf.itinerai.agent;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaTool;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.usf.itinerai.feasibility.FeasibilityService;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.routing.RouteService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// the tools Claude can call, and what each one does to the draft. Every edit re-checks the whole day, so Claude
// always sees whether a change helped
@Component
public class AgentTools {

    static final String GET_TRAVEL_TIME = "get_travel_time";
    static final String MOVE_ITEM = "move_item";
    static final String REMOVE_ITEM = "remove_item";

    private static final List<String> MODES = Arrays.stream(TravelMode.values()).map(Enum::name).toList();

    // strict schemas, so Claude's inputs always have every field with the right type
    private static final List<BetaTool> DEFINITIONS = List.of(
            tool(GET_TRAVEL_TIME, "How many minutes it takes to get from where one item ends to where another "
                            + "starts. Use it to plan a different order before moving items.",
                    property("from_item_id", "integer", "Id of the item the traveler leaves from."),
                    property("to_item_id", "integer", "Id of the item the traveler goes to."),
                    Map.entry("mode", Map.of("type", "string", "enum", MODES,
                            "description", "How the traveler gets there; usually the trip's travel mode."))),
            tool(MOVE_ITEM, "Gives an item new start and end times in the draft. Returns the updated schedule "
                            + "and its conflicts.",
                    property("item_id", "integer", "Id of the item to move."),
                    property("start_time", "string", "New start, 24-hour HH:mm, e.g. 09:30."),
                    property("end_time", "string", "New end, 24-hour HH:mm, later the same day."),
                    property("reason", "string", "One short sentence for the traveler on why this change helps.")),
            tool(REMOVE_ITEM, "Removes an item from the draft. Only works when the traveler allowed removals. "
                            + "Returns the updated schedule and its conflicts.",
                    property("item_id", "integer", "Id of the item to remove."),
                    property("reason", "string", "One short sentence for the traveler on why it has to go.")));

    private final RouteService routeService;
    private final FeasibilityService feasibilityService;

    public AgentTools(RouteService routeService, FeasibilityService feasibilityService) {
        this.routeService = routeService;
        this.feasibilityService = feasibilityService;
    }

    public List<BetaTool> definitions() {
        return DEFINITIONS;
    }

    // runs one tool call against the draft. A call the draft rejects comes back as an error result Claude can
    // correct; a RoutingException is passed on, since the day can't be checked without travel times
    public ToolResult run(String name, JsonValue input, ItineraryDraft draft) {
        try {
            return switch (name) {
                case GET_TRAVEL_TIME -> ToolResult.ok(travelTime(input.convert(TravelTimeInput.class), draft));
                case MOVE_ITEM -> {
                    MoveInput move = input.convert(MoveInput.class);
                    draft.move(move.itemId(), time(move.startTime(), "start_time"), time(move.endTime(), "end_time"),
                            move.reason());
                    yield ToolResult.ok(state(draft));
                }
                case REMOVE_ITEM -> {
                    RemoveInput remove = input.convert(RemoveInput.class);
                    draft.remove(remove.itemId(), remove.reason());
                    yield ToolResult.ok(state(draft));
                }
                default -> ToolResult.error("there is no tool called " + name);
            };
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String travelTime(TravelTimeInput input, ItineraryDraft draft) {
        ItineraryItem from = draft.item(input.fromItemId());
        ItineraryItem to = draft.item(input.toItemId());
        Location origin = from.getLocation();
        // a transportation item starts at its origin; its location is where it ends up
        Location destination = to instanceof Transportation leg ? leg.getOrigin() : to.getLocation();
        Optional<Duration> travelTime = routeService.travelTime(origin, destination, input.mode());
        if (travelTime.isEmpty()) {
            return "There is no " + input.mode() + " route from " + origin.getName() + " to " + destination.getName()
                    + ".";
        }
        return "About " + minutesRoundedUp(travelTime.get()) + " min from " + origin.getName() + " to "
                + destination.getName() + " by " + input.mode() + ".";
    }

    private String state(ItineraryDraft draft) {
        return "Done.\n\n" + OptimizerPrompt.state(draft, feasibilityService.check(draft.toTrip()));
    }

    private static LocalTime time(String value, String field) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException(field + " must be a 24-hour time like 09:30, not \"" + value + "\"");
        }
    }

    private static long minutesRoundedUp(Duration duration) {
        long minutes = duration.toMinutes();
        return duration.equals(Duration.ofMinutes(minutes)) ? minutes : minutes + 1;
    }

    @SafeVarargs
    private static BetaTool tool(String name, String description, Map.Entry<String, Object>... properties) {
        Map<String, JsonValue> schema = new LinkedHashMap<>();
        for (Map.Entry<String, Object> property : properties) {
            schema.put(property.getKey(), JsonValue.from(property.getValue()));
        }
        return BetaTool.builder()
                .name(name)
                .description(description)
                .inputSchema(BetaTool.InputSchema.builder()
                        .properties(BetaTool.InputSchema.Properties.builder().additionalProperties(schema).build())
                        .required(List.copyOf(schema.keySet()))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .strict(true)
                .build();
    }

    private static Map.Entry<String, Object> property(String name, String type, String description) {
        return Map.entry(name, Map.of("type", type, "description", description));
    }

    // what a tool call returns to Claude: text, flagged as an error when the call was rejected
    public record ToolResult(String content, boolean isError) {

        static ToolResult ok(String content) {
            return new ToolResult(content, false);
        }

        static ToolResult error(String content) {
            return new ToolResult(content, true);
        }
    }

    // the JSON inputs of each tool, as Claude sends them
    record TravelTimeInput(@JsonProperty("from_item_id") long fromItemId, @JsonProperty("to_item_id") long toItemId,
                           @JsonProperty("mode") TravelMode mode) {
    }

    record MoveInput(@JsonProperty("item_id") long itemId, @JsonProperty("start_time") String startTime,
                     @JsonProperty("end_time") String endTime, @JsonProperty("reason") String reason) {
    }

    record RemoveInput(@JsonProperty("item_id") long itemId, @JsonProperty("reason") String reason) {
    }
}
