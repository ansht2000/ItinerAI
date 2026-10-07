package com.usf.itinerai.web.dto;

import com.usf.itinerai.itinerary.Reservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

public record ReservationRequest(
        @NotBlank String title,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        String notes,
        @NotNull @Valid LocationRequest location,
        String confirmationCode,
        @Positive Integer partySize) implements ItemRequest {

    @Override
    public Reservation toItem(Long id) {
        return new Reservation(id, title, location.toLocation(), startTime, endTime, notes, confirmationCode,
                partySize);
    }
}
