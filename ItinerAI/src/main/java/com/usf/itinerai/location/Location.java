package com.usf.itinerai.location;

import java.time.LocalTime;

// a place an itinerary item happens at, with its daily operating hours; immutable once constructed
public class Location {

    private final String name;
    private final double latitude;
    private final double longitude;
    private final LocalTime openTime;
    private final LocalTime closeTime;

    public Location(String name, double latitude, double longitude, LocalTime openTime, LocalTime closeTime) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        // written as !(in range) so NaN is rejected too
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        if (openTime == null || closeTime == null) {
            throw new IllegalArgumentException("openTime and closeTime must not be null");
        }
        // known limitation: overnight (e.g. 18:00-02:00) and 24-hour places are rejected; out of scope for Milestone 1
        if (!closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("closeTime must be after openTime");
        }
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
