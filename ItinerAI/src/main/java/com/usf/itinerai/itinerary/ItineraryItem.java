package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;

import java.time.LocalTime;

// base for everything that can be scheduled in a day; immutable once constructed
public abstract class ItineraryItem {

    private final Long id;
    private final String title;
    private final Location location;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String notes;

    // notes is optional and may be null
    protected ItineraryItem(String title, Location location, LocalTime startTime, LocalTime endTime, String notes) {
        this(null, title, location, startTime, endTime, notes);
    }

    // same as above, plus the database id (null for an item that hasn't been saved yet)
    protected ItineraryItem(Long id, String title, Location location, LocalTime startTime, LocalTime endTime,
                            String notes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime and endTime must not be null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.id = id;
        this.title = title;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.notes = notes;
    }

    // null until the item has been saved
    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
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

    public String getNotes() {
        return notes;
    }
}
