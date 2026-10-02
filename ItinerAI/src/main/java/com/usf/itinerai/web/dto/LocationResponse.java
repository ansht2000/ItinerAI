package com.usf.itinerai.web.dto;

public record LocationResponse(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String placeId) {
}
