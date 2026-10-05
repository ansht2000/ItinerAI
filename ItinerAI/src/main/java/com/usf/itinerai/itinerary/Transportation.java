package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;

import java.time.LocalTime;

// travel from origin to the inherited location, which is the destination
public class Transportation extends ItineraryItem {

    private final Location origin;
    private final TravelMode mode;

    // notes is optional and may be null
    public Transportation(String title, Location location, LocalTime startTime, LocalTime endTime, String notes,
                          Location origin, TravelMode mode) {
        super(title, location, startTime, endTime, notes);
        if (origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        this.origin = origin;
        this.mode = mode;
    }

    public Location getOrigin() {
        return origin;
    }

    public TravelMode getMode() {
        return mode;
    }
}
