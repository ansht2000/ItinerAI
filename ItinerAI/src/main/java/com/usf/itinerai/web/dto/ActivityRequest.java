package com.usf.itinerai.web.dto;

import com.usf.itinerai.itinerary.Activity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ActivityRequest(
        @NotBlank String title,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        String notes,
        @NotNull @Valid LocationRequest location,
        String category) implements ItemRequest {

    @Override
    public Activity toItem(Long id) {
        return new Activity(id, title, location.toLocation(), startTime, endTime, notes, category);
    }
}
