package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.trip.Trip;

import java.util.ArrayList;
import java.util.List;

// flags every pair of items whose time windows intersect; items that only touch (one ends as the next starts) are fine
public class OverlapConstraint implements Constraint {

    @Override
    public List<Conflict> check(Trip trip) {
        List<ItineraryItem> items = trip.getItinerary().getItems();
        List<Conflict> conflicts = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItineraryItem first = items.get(i);
            for (int j = i + 1; j < items.size(); j++) {
                ItineraryItem second = items.get(j);
                // items are sorted by startTime, so once one starts after first ends, the rest do too
                if (!second.getStartTime().isBefore(first.getEndTime())) {
                    break;
                }
                conflicts.add(new Conflict(describe(first) + " overlaps " + describe(second), List.of(first, second)));
            }
        }
        return conflicts;
    }

    private static String describe(ItineraryItem item) {
        return "\"" + item.getTitle() + "\" (" + item.getStartTime() + "-" + item.getEndTime() + ")";
    }
}
