package com.usf.itinerai.web.dto;

import java.util.List;

// type is the kind of conflict, e.g. "OVERLAP" or "INSUFFICIENT_TRAVEL_TIME"
public record ConflictResponse(
        String type,
        List<Long> itemIds,
        String reason) {
}
