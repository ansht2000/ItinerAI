package com.usf.itinerai.agent;

// the Claude API can't be used: no key is set, or the API failed (outage, rate limit, rejected key)
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }

    public AgentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
