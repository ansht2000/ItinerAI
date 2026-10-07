package com.usf.itinerai.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalTime;

// opening hours are left out of the JSON when the place has none
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationResponse(
        String name,
        double latitude,
        double longitude,
        LocalTime openTime,
        LocalTime closeTime) {
}
