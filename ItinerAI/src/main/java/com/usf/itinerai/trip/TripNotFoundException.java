package com.usf.itinerai.trip;

public class TripNotFoundException extends RuntimeException {

    public TripNotFoundException(long tripId) {
        super("trip " + tripId + " not found");
    }
}
