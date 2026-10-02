package com.usf.itinerai.web.dto;

import com.usf.itinerai.itinerary.TravelMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record TransportationRequest(
        @NotBlank String title,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        String notes,
        @NotNull @Valid LocationRequest origin,
        @NotNull @Valid LocationRequest destination,
        @NotNull TravelMode mode) implements ItemRequest {
}
