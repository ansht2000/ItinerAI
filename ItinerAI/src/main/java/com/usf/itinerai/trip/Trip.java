package com.usf.itinerai.trip;

import com.usf.itinerai.itinerary.Itinerary;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.TravelMode;

import java.time.LocalDate;
import java.time.ZoneId;

// a one-day trip; its schedule starts empty and items are added afterwards
public class Trip {

    private final String name;
    private final String destination;
    private final LocalDate date;
    private final ZoneId timeZone;
    private final TravelMode defaultTravelMode;
    private final Itinerary itinerary = new Itinerary();

    public Trip(String name, String destination, LocalDate date, ZoneId timeZone, TravelMode defaultTravelMode) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be null or blank");
        }
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (timeZone == null) {
            throw new IllegalArgumentException("timeZone must not be null");
        }
        if (defaultTravelMode == null) {
            throw new IllegalArgumentException("defaultTravelMode must not be null");
        }
        this.name = name;
        this.destination = destination;
        this.date = date;
        this.timeZone = timeZone;
        this.defaultTravelMode = defaultTravelMode;
    }

    public String getName() {
        return name;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDate getDate() {
        return date;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public TravelMode getDefaultTravelMode() {
        return defaultTravelMode;
    }

    public Itinerary getItinerary() {
        return itinerary;
    }

    public void addItem(ItineraryItem item) {
        itinerary.addItem(item);
    }
}
