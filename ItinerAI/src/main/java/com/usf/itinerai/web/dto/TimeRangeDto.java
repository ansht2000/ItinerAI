package com.usf.itinerai.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.usf.itinerai.agent.TimeRange;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

// an item's times in a proposed change ("before" or "after")
public record TimeRangeDto(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {

    // missing times are reported by @NotNull, so only compare when both are present
    @JsonIgnore
    @AssertTrue(message = "startTime must be before endTime")
    public boolean isRangeValid() {
        return startTime == null || endTime == null || startTime.isBefore(endTime);
    }

    public TimeRange toTimeRange() {
        return new TimeRange(startTime, endTime);
    }

    // null stays null: a REMOVE change has no "after"
    public static TimeRangeDto from(TimeRange range) {
        return range == null ? null : new TimeRangeDto(range.start(), range.end());
    }
}
