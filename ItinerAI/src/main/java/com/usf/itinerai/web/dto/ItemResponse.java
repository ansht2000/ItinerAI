package com.usf.itinerai.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;

import java.time.LocalTime;

// one shape for every item type; fields that don't apply to the type are null and left out of the JSON
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemResponse(
        Long id,
        String type,
        String title,
        LocalTime startTime,
        LocalTime endTime,
        String notes,
        // activity and reservation
        LocationResponse location,
        // activity
        String category,
        // reservation
        String confirmationCode,
        Integer partySize,
        // transportation
        LocationResponse origin,
        LocationResponse destination,
        TravelMode mode) {

    // type uses the same names as the "type" field of ItemRequest
    public static ItemResponse from(ItineraryItem item) {
        LocationResponse location = LocationResponse.from(item.getLocation());
        return switch (item) {
            case Activity activity -> new ItemResponse(item.getId(), "activity", item.getTitle(),
                    item.getStartTime(), item.getEndTime(), item.getNotes(),
                    location, activity.getCategory(), null, null, null, null, null);
            case Reservation reservation -> new ItemResponse(item.getId(), "reservation", item.getTitle(),
                    item.getStartTime(), item.getEndTime(), item.getNotes(),
                    location, null, reservation.getConfirmationCode(), reservation.getPartySize(), null, null, null);
            // a transportation item's location is its destination
            case Transportation transportation -> new ItemResponse(item.getId(), "transportation", item.getTitle(),
                    item.getStartTime(), item.getEndTime(), item.getNotes(),
                    null, null, null, null, LocationResponse.from(transportation.getOrigin()), location,
                    transportation.getMode());
            default -> throw new IllegalArgumentException("unknown item type: " + item.getClass().getSimpleName());
        };
    }
}
