package com.usf.itinerai.agent;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Arrays;
import java.util.stream.Collectors;

// Claude's replies as the API would send them, parsed by the SDK itself, for scripting a fake ModelClient
public final class ScriptedReplies {

    private ScriptedReplies() {
    }

    // a reply asking for one or more tool calls, each made with call(...)
    public static BetaMessage toolUse(String... calls) {
        return reply("tool_use", Arrays.stream(calls).collect(Collectors.joining(", ")));
    }

    // a final reply with Claude's summary for the traveler
    public static BetaMessage endTurn(String text) {
        return reply("end_turn", """
                {"type": "text", "text": %s}""".formatted(quote(text)));
    }

    public static BetaMessage stoppedWith(String stopReason) {
        return reply(stopReason, "");
    }

    public static String call(String id, String tool, String inputJson) {
        return """
                {"type": "tool_use", "id": "%s", "name": "%s", "input": %s}""".formatted(id, tool, inputJson);
    }

    // the JSON body a request would send, for checking what the agent told Claude
    public static String body(MessageCreateParams request) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(request._body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BetaMessage reply(String stopReason, String content) {
        String json = """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-sonnet-5-5",
                 "content": [%s], "stop_reason": "%s", "stop_sequence": null,
                 "usage": {"input_tokens": 1000, "output_tokens": 100, "cache_read_input_tokens": 800,
                           "cache_creation_input_tokens": 0}}
                """.formatted(content, stopReason);
        try {
            return ObjectMappers.jsonMapper().readValue(json, BetaMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String quote(String text) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
