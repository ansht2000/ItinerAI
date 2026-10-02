package com.usf.itinerai.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.usf.itinerai.itinerary.TravelMode;

import java.time.LocalTime;

// one shape for every item type; fields that don't apply to the type are null and left out of the JSON
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemResponse(
        Long id,
        String type,
        String title,
        LocalTime startTime,
        LocalTime endTime,
        String notes,
        // activity and reservation
        LocationResponse location,
        // activity
        String category,
        // reservation
        String confirmationCode,
        Integer partySize,
        // transportation
        LocationResponse origin,
        LocationResponse destination,
        TravelMode mode) {
}
