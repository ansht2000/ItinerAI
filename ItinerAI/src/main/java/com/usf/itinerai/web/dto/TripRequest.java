package com.usf.itinerai.web.dto;

import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.trip.Trip;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.ZoneId;

// body for creating (POST) or replacing (PUT) a trip; items are managed through their own endpoints
public record TripRequest(
        @NotBlank String name,
        @NotBlank String destination,
        @NotNull LocalDate date,
        @NotNull ZoneId timeZone,
        @NotNull TravelMode defaultTravelMode) {

    // id is null for a new trip, or the id from the URL when replacing one
    public Trip toTrip(Long id) {
        return new Trip(id, name, destination, date, timeZone, defaultTravelMode);
    }
}
