package com.usf.itinerai.trip;

// also used when the item exists but belongs to a different trip
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(long tripId, long itemId) {
        super("item " + itemId + " not found in trip " + tripId);
    }
}
