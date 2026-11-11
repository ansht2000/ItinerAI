package com.usf.itinerai.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// settings under itinerai.agent in application.properties
@ConfigurationProperties("itinerai.agent")
public record AgentProperties(
        // empty until ANTHROPIC_API_KEY is set; the agent then answers 503 instead of calling Claude
        String apiKey,
        @DefaultValue("claude-sonnet-5-5") String model,
        // how hard the model thinks at each step: low, medium, high, xhigh or max
        @DefaultValue("high") String effort,
        // calls to Claude before the agent stops and returns what it has
        @DefaultValue("8") int maxRounds,
        // no new round starts after this, so a run can go past it by one round
        @DefaultValue("90s") Duration timeLimit,
        @DefaultValue("16000") long maxTokens) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
