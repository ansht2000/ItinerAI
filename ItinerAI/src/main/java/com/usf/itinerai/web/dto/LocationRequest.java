package com.usf.itinerai.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record LocationRequest(
        @NotBlank String name,
        String address,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        String placeId) {

    // the routing API needs at least one way to find the place
    @AssertTrue(message = "provide an address, a placeId, or both latitude and longitude")
    public boolean isLocatable() {
        boolean hasCoordinates = latitude != null && longitude != null;
        return hasCoordinates || hasText(address) || hasText(placeId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
