package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;

import java.time.LocalTime;

// base for everything that can be scheduled in a day; immutable once constructed
public abstract class ItineraryItem {

    private final Location location;
    private final LocalTime startTime;
    private final LocalTime endTime;

    protected ItineraryItem(Location location, LocalTime startTime, LocalTime endTime) {
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime must not be null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Location getLocation() {
        return location;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
