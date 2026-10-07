package com.usf.itinerai.trip;

import com.usf.itinerai.itinerary.ItineraryItem;
import org.springframework.stereotype.Service;

import java.util.List;

// what the controllers call; works with domain objects and throws a not-found exception instead of returning empty
@Service
public class TripService {

    private final TripRepository trips;

    public TripService(TripRepository trips) {
        this.trips = trips;
    }

    public Trip createTrip(Trip trip) {
        return trips.create(trip);
    }

    public List<Trip> getTrips() {
        return trips.findAll();
    }

    public Trip getTrip(long tripId) {
        return trips.findById(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
    }

    // trip carries the id of the trip to change; its items are left as they are
    public Trip updateTrip(Trip trip) {
        return trips.update(trip).orElseThrow(() -> new TripNotFoundException(trip.getId()));
    }

    public void deleteTrip(long tripId) {
        if (!trips.deleteById(tripId)) {
            throw new TripNotFoundException(tripId);
        }
    }

    public List<ItineraryItem> getItems(long tripId) {
        return getTrip(tripId).getItinerary().getItems();
    }

    public ItineraryItem addItem(long tripId, ItineraryItem item) {
        return trips.addItem(tripId, item).orElseThrow(() -> new TripNotFoundException(tripId));
    }

    public ItineraryItem getItem(long tripId, long itemId) {
        return trips.findItem(tripId, itemId).orElseThrow(() -> new ItemNotFoundException(tripId, itemId));
    }

    // item carries the id of the item to replace
    public ItineraryItem updateItem(long tripId, ItineraryItem item) {
        return trips.updateItem(tripId, item).orElseThrow(() -> new ItemNotFoundException(tripId, item.getId()));
    }

    public void deleteItem(long tripId, long itemId) {
        if (!trips.deleteItem(tripId, itemId)) {
            throw new ItemNotFoundException(tripId, itemId);
        }
    }
}
