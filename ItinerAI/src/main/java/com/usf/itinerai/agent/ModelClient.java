package com.usf.itinerai.agent;

import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;

// sends one request to Claude and returns its reply. An interface, like RouteService, so tests can script the
// replies without an API key or network
@FunctionalInterface
public interface ModelClient {

    // throws AgentUnavailableException when the API can't be used (no key, outage, rejected request)
    BetaMessage send(MessageCreateParams params);
}
