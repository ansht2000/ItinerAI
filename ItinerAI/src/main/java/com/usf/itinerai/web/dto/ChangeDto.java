package com.usf.itinerai.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.usf.itinerai.agent.ChangeType;
import com.usf.itinerai.agent.ProposedChange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// one change in a proposal; the UI sends these back unchanged to refine or apply. title and reason are for
// people and are ignored when applying
public record ChangeDto(
        @NotNull ChangeType type,
        @NotNull Long itemId,
        String title,
        @NotNull @Valid TimeRangeDto before,
        @Valid TimeRangeDto after,
        @Size(max = 1000) String reason) {

    @JsonIgnore
    @AssertTrue(message = "after is required for RESCHEDULE and must be null for REMOVE")
    public boolean isAfterValid() {
        return type == null || (type == ChangeType.RESCHEDULE) == (after != null);
    }

    public ProposedChange toChange() {
        return new ProposedChange(type, itemId, title, before.toTimeRange(),
                after == null ? null : after.toTimeRange(), reason);
    }

    public static ChangeDto from(ProposedChange change) {
        return new ChangeDto(change.type(), change.itemId(), change.title(), TimeRangeDto.from(change.before()),
                TimeRangeDto.from(change.after()), change.reason());
    }
}
