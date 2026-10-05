package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;

import java.time.LocalTime;

public class Reservation extends ItineraryItem {

    private final String confirmationCode;
    private final Integer partySize;

    // notes, confirmationCode and partySize are optional and may be null
    public Reservation(String title, Location location, LocalTime startTime, LocalTime endTime, String notes,
                       String confirmationCode, Integer partySize) {
        super(title, location, startTime, endTime, notes);
        if (partySize != null && partySize <= 0) {
            throw new IllegalArgumentException("partySize must be positive");
        }
        this.confirmationCode = confirmationCode;
        this.partySize = partySize;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public Integer getPartySize() {
        return partySize;
    }
}
