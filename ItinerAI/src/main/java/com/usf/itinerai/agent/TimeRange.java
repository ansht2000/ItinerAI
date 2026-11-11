package com.usf.itinerai.agent;

import com.usf.itinerai.itinerary.ItineraryItem;

import java.time.Duration;
import java.time.LocalTime;

// an item's start and end on the trip's day
public record TimeRange(LocalTime start, LocalTime end) {

    public TimeRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end must not be null");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public static TimeRange of(ItineraryItem item) {
        return new TimeRange(item.getStartTime(), item.getEndTime());
    }

    public Duration length() {
        return Duration.between(start, end);
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }
}
