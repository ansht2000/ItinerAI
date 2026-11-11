package com.usf.itinerai.agent;

import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaFallbacksParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// the agent loop: send the conversation to Claude, run the tools it asks for against the draft, send the results
// back, and repeat until Claude says it's done or a limit is hit. The draft holds the result either way
@Service
public class ItineraryAgent {

    private static final Logger log = LoggerFactory.getLogger(ItineraryAgent.class);

    // if a safety filter ever declines a request, the API retries it on another model instead of failing
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    static final String UNFINISHED_SUMMARY =
            "The assistant stopped before it finished, so this is the best version it found.";
    static final String NO_SUMMARY = "The assistant finished without explaining its changes.";

    private final ModelClient model;
    private final AgentTools tools;
    private final AgentProperties properties;
    private final Clock clock;

    @Autowired
    public ItineraryAgent(ModelClient model, AgentTools tools, AgentProperties properties) {
        this(model, tools, properties, Clock.systemUTC());
    }

    // the clock lets tests run past the time limit without waiting
    ItineraryAgent(ModelClient model, AgentTools tools, AgentProperties properties, Clock clock) {
        this.model = model;
        this.tools = tools;
        this.properties = properties;
        this.clock = clock;
    }

    public Result run(ItineraryDraft draft, String firstMessage) {
        Instant started = clock.instant();
        Instant deadline = started.plus(properties.timeLimit());
        // only ever appended to: Claude's replies go back unchanged, thinking included
        List<BetaMessageParam> conversation = new ArrayList<>();
        conversation.add(BetaMessageParam.ofUser(firstMessage));
        TokenCount tokens = new TokenCount();
        int rounds = 0;
        Result result = null;
        while (result == null) {
            if (rounds == properties.maxRounds() || (rounds > 0 && !clock.instant().isBefore(deadline))) {
                result = Result.unfinished();
                break;
            }
            rounds++;
            BetaMessage reply = model.send(request(conversation));
            tokens.add(reply.usage());
            conversation.add(reply.toParam());
            BetaStopReason stopReason = reply.stopReason().orElse(null);
            if (BetaStopReason.TOOL_USE.equals(stopReason)) {
                List<BetaContentBlockParam> results = runTools(reply, draft);
                if (results.isEmpty()) {
                    result = Result.unfinished();
                } else {
                    // all of a turn's results go back together, in the order Claude asked for them
                    conversation.add(BetaMessageParam.ofUserOfContentBlockParams(results));
                }
            } else if (BetaStopReason.END_TURN.equals(stopReason) || BetaStopReason.STOP_SEQUENCE.equals(stopReason)) {
                String summary = text(reply);
                result = Result.finished(summary.isEmpty() ? NO_SUMMARY : summary);
            } else {
                // a refusal, a cut-off reply or anything unexpected: keep whatever the draft has so far
                log.warn("agent for trip {} stopped early: stop reason {}", draft.saved().getId(), stopReason);
                result = Result.unfinished();
            }
        }
        log.info("agent for trip {}: {} rounds in {} ms, {}; tokens: {} input ({} cache reads, {} cache writes), "
                        + "{} output", draft.saved().getId(), rounds,
                Duration.between(started, clock.instant()).toMillis(), result.finished() ? "finished" : "unfinished",
                tokens.input, tokens.cacheRead, tokens.cacheWrite, tokens.output);
        return result;
    }

    private MessageCreateParams request(List<BetaMessageParam> conversation) {
        MessageCreateParams.Builder request = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(OptimizerPrompt.SYSTEM)
                .messages(List.copyOf(conversation))
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.of(properties.effort()))
                        .build())
                // automatic caching: each round resends the whole conversation, so later rounds mostly hit the cache
                .cacheControl(BetaCacheControlEphemeral.builder().build())
                .addBeta(FALLBACK_BETA)
                .fallbacks(BetaFallbacksParam.ofDefault());
        tools.definitions().forEach(request::addTool);
        return request.build();
    }

    private List<BetaContentBlockParam> runTools(BetaMessage reply, ItineraryDraft draft) {
        List<BetaContentBlockParam> results = new ArrayList<>();
        for (BetaContentBlock block : reply.content()) {
            if (block.isToolUse()) {
                BetaToolUseBlock call = block.asToolUse();
                AgentTools.ToolResult result = tools.run(call.name(), call._input(), draft);
                results.add(BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                        .toolUseId(call.id())
                        .content(result.content())
                        .isError(result.isError())
                        .build()));
            }
        }
        return results;
    }

    private static String text(BetaMessage reply) {
        return reply.content().stream()
                .flatMap(block -> block.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining("\n"))
                .strip();
    }

    // how the run ended, plus Claude's explanation for the traveler
    public record Result(boolean finished, String summary) {

        static Result finished(String summary) {
            return new Result(true, summary);
        }

        static Result unfinished() {
            return new Result(false, UNFINISHED_SUMMARY);
        }
    }

    // token usage across a run's rounds, for the log line that tracks cost
    private static final class TokenCount {

        private long input;
        private long output;
        private long cacheRead;
        private long cacheWrite;

        void add(BetaUsage usage) {
            input += usage.inputTokens();
            output += usage.outputTokens();
            cacheRead += usage.cacheReadInputTokens().orElse(0L);
            cacheWrite += usage.cacheCreationInputTokens().orElse(0L);
        }
    }
}
