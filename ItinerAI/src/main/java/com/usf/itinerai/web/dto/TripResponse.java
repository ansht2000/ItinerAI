package com.usf.itinerai.web.dto;

import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.trip.Trip;

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

    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getName(),
                trip.getDestination(),
                trip.getDate(),
                trip.getTimeZone(),
                trip.getDefaultTravelMode(),
                trip.getItinerary().getItems().stream().map(ItemResponse::from).toList());
    }
}
