package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;

import java.time.LocalTime;

public class Activity extends ItineraryItem {

    private final String category;

    // notes and category are optional and may be null
    public Activity(String title, Location location, LocalTime startTime, LocalTime endTime, String notes,
                    String category) {
        this(null, title, location, startTime, endTime, notes, category);
    }

    // same as above, plus the database id (null for an activity that hasn't been saved yet)
    public Activity(Long id, String title, Location location, LocalTime startTime, LocalTime endTime, String notes,
                    String category) {
        super(id, title, location, startTime, endTime, notes);
        this.category = category;
    }

    public String getCategory() {
        return category;
    }
}
