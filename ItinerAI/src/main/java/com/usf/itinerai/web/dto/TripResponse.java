package com.usf.itinerai.web.dto;

import com.usf.itinerai.itinerary.TravelMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public record TripResponse(
        Long id,
        String name,
        String destination,
        LocalDate date,
        ZoneId timeZone,
        TravelMode defaultTravelMode,
        List<ItemResponse> items) {
}
