package com.usf.itinerai.web.dto;

import com.usf.itinerai.location.Location;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

// mirrors the Location domain class: coordinates are required, opening hours are optional
public record LocationRequest(
        @NotBlank String name,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        LocalTime openTime,
        LocalTime closeTime) {

    // same rule as Location: only checked when both are given, and overnight hours aren't supported yet
    @AssertTrue(message = "closeTime must be after openTime")
    public boolean isHoursValid() {
        return openTime == null || closeTime == null || closeTime.isAfter(openTime);
    }

    public Location toLocation() {
        return new Location(name, latitude, longitude, openTime, closeTime);
    }
}
