package com.usf.itinerai.web.dto;

import com.usf.itinerai.agent.ProposedChange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// body for applying a proposal: its changes, exactly as the optimize response returned them
public record ApplyRequest(@NotNull List<@NotNull @Valid ChangeDto> changes) {

    public List<ProposedChange> toChanges() {
        return changes.stream().map(ChangeDto::toChange).toList();
    }
}
