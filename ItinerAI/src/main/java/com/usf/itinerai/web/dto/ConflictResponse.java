package com.usf.itinerai.web.dto;

import com.usf.itinerai.feasibility.Conflict;
import com.usf.itinerai.feasibility.ConflictType;
import com.usf.itinerai.itinerary.ItineraryItem;

import java.util.List;

public record ConflictResponse(
        ConflictType type,
        List<Long> itemIds,
        String reason) {

    public static ConflictResponse from(Conflict conflict) {
        return new ConflictResponse(
                conflict.type(),
                conflict.items().stream().map(ItineraryItem::getId).toList(),
                conflict.message());
    }
}
