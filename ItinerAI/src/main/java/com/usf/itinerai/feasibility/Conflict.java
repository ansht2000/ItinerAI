package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.ItineraryItem;

import java.util.List;

// one problem a constraint found, with the items involved (e.g. the two items that overlap)
public record Conflict(String message, List<ItineraryItem> items) {

    public Conflict {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        for (ItineraryItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items must not contain null");
            }
        }
        // copied so the conflict can't change after it's reported
        items = List.copyOf(items);
    }
}
