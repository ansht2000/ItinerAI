package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;

import java.time.LocalTime;

public class Activity extends ItineraryItem {

    private final String category;

    // notes and category are optional and may be null
    public Activity(String title, Location location, LocalTime startTime, LocalTime endTime, String notes,
                    String category) {
        super(title, location, startTime, endTime, notes);
        this.category = category;
    }

    public String getCategory() {
        return category;
    }
}
