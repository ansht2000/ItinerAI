package com.usf.itinerai.web.dto;

import com.usf.itinerai.agent.OptimizeCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// body for asking the agent for a proposal; every field is optional, and the body may be left out entirely.
// To refine, send the previous response's changes and every feedback message so far
public record OptimizeRequest(
        @Size(max = 500) String goal,
        // left out: every reservation is locked; [] locks nothing
        List<@NotNull Long> lockedItemIds,
        Boolean allowRemovals,
        List<@NotNull @Valid ChangeDto> changes,
        List<@NotBlank @Size(max = 500) String> feedback) {

    public static final OptimizeRequest EMPTY = new OptimizeRequest(null, null, null, null, null);

    public OptimizeCommand toCommand() {
        return new OptimizeCommand(goal, lockedItemIds, Boolean.TRUE.equals(allowRemovals),
                changes == null ? List.of() : changes.stream().map(ChangeDto::toChange).toList(), feedback);
    }
}
