package com.usf.itinerai.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.usf.itinerai.itinerary.ItineraryItem;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalTime;

// the JSON "type" field ("activity", "reservation" or "transportation") picks the subtype
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityRequest.class, name = "activity"),
        @JsonSubTypes.Type(value = ReservationRequest.class, name = "reservation"),
        @JsonSubTypes.Type(value = TransportationRequest.class, name = "transportation")
})
public sealed interface ItemRequest permits ActivityRequest, ReservationRequest, TransportationRequest {

    String title();

    LocalTime startTime();

    LocalTime endTime();

    String notes();

    // id is null for a new item, or the id from the URL when replacing one
    ItineraryItem toItem(Long id);

    // missing times are reported by @NotNull on each subtype, so only compare when both are present
    @AssertTrue(message = "startTime must be before endTime")
    default boolean isTimeRangeValid() {
        return startTime() == null || endTime() == null || startTime().isBefore(endTime());
    }
}
