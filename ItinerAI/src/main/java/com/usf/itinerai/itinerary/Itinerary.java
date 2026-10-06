package com.usf.itinerai.itinerary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// one day's schedule, kept sorted by startTime; overlapping items are allowed and left to the feasibility checks
public class Itinerary {

    private static final Comparator<ItineraryItem> BY_START_TIME = Comparator.comparing(ItineraryItem::getStartTime);

    private final List<ItineraryItem> items = new ArrayList<>();

    public Itinerary() {
    }

    // the list is copied, so later changes to it don't affect this itinerary
    public Itinerary(List<ItineraryItem> initialItems) {
        if (initialItems == null) {
            throw new IllegalArgumentException("initialItems must not be null");
        }
        for (ItineraryItem item : initialItems) {
            requireNonNull(item);
        }
        items.addAll(initialItems);
        // stable sort, so items with the same startTime keep their given order
        items.sort(BY_START_TIME);
    }

    // goes after any items that start at the same time
    public void addItem(ItineraryItem item) {
        requireNonNull(item);
        int index = 0;
        while (index < items.size() && !items.get(index).getStartTime().isAfter(item.getStartTime())) {
            index++;
        }
        items.add(index, item);
    }

    // read-only view that reflects later addItem calls
    public List<ItineraryItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private static void requireNonNull(ItineraryItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
    }
}
