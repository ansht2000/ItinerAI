package com.usf.itinerai.feasibility;

import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.trip.Trip;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// flags items that aren't entirely within their place's opening hours: an item must start at or after
// opening and end at or before closing
@Component
public class OperatingHoursConstraint implements Constraint {

    @Override
    public List<Conflict> check(Trip trip) {
        List<Conflict> conflicts = new ArrayList<>();
        for (ItineraryItem item : trip.getItinerary().getItems()) {
            // a transportation item's location is where it arrives, not a place it spends time at
            if (item instanceof Transportation) {
                continue;
            }
            Location place = item.getLocation();
            // like Location itself, a place without both hours counts as always open
            if (place.getOpenTime() == null || place.getCloseTime() == null) {
                continue;
            }
            boolean startsTooEarly = item.getStartTime().isBefore(place.getOpenTime());
            boolean endsTooLate = item.getEndTime().isAfter(place.getCloseTime());
            if (startsTooEarly || endsTooLate) {
                conflicts.add(new Conflict(ConflictType.OUTSIDE_OPERATING_HOURS,
                        describe(item, place, startsTooEarly, endsTooLate), List.of(item)));
            }
        }
        return conflicts;
    }

    private static String describe(ItineraryItem item, Location place, boolean startsTooEarly, boolean endsTooLate) {
        String scheduled = "\"" + item.getTitle() + "\" (" + item.getStartTime() + "-" + item.getEndTime() + ")";
        String hours = " (open " + place.getOpenTime() + "-" + place.getCloseTime() + ")";
        boolean entirelyWhileClosed = !item.getEndTime().isAfter(place.getOpenTime())
                || !item.getStartTime().isBefore(place.getCloseTime());
        if (entirelyWhileClosed) {
            return scheduled + " is scheduled while " + place.getName() + " is closed" + hours;
        }
        if (startsTooEarly && endsTooLate) {
            return scheduled + " starts before " + place.getName() + " opens and ends after it closes" + hours;
        }
        if (startsTooEarly) {
            return scheduled + " starts before " + place.getName() + " opens" + hours;
        }
        return scheduled + " ends after " + place.getName() + " closes" + hours;
    }
}
