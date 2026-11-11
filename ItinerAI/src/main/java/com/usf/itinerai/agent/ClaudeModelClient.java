package com.usf.itinerai.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;

// ModelClient backed by the official Anthropic Java SDK
@Component
public class ClaudeModelClient implements ModelClient, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ClaudeModelClient.class);

    private final AgentProperties properties;
    // created on first use, so the app starts without a key and only the agent answers 503
    private AnthropicClient client;

    public ClaudeModelClient(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public BetaMessage send(MessageCreateParams params) {
        AnthropicClient anthropic = client();
        try {
            return anthropic.beta().messages().create(params);
        } catch (AnthropicServiceException e) {
            // the SDK already retried what's worth retrying (rate limits, overload, 5xx)
            log.warn("Claude API returned {}", e.statusCode(), e);
            throw new AgentUnavailableException("Claude API returned " + e.statusCode() + apiMessage(e), e);
        } catch (AnthropicException e) {
            log.warn("Claude API call failed", e);
            throw new AgentUnavailableException("could not reach the Claude API", e);
        }
    }

    private synchronized AnthropicClient client() {
        if (!properties.hasApiKey()) {
            throw new AgentUnavailableException("the itinerary assistant isn't set up: ANTHROPIC_API_KEY is not set");
        }
        if (client == null) {
            // the key comes from Spring (which reads .env), since the SDK's fromEnv() only sees real env variables
            client = AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build();
        }
        return client;
    }

    @Override
    public synchronized void destroy() {
        if (client != null) {
            client.close();
        }
    }

    // the API's own explanation (e.g. "invalid x-api-key") when the error body has one
    private static String apiMessage(AnthropicServiceException e) {
        try {
            Map<?, ?> body = e.body().convert(Map.class);
            if (body != null && body.get("error") instanceof Map<?, ?> error
                    && error.get("message") instanceof String message) {
                return ": " + message;
            }
        } catch (RuntimeException ignored) {
            // not the API's JSON error format
        }
        return "";
    }
}
