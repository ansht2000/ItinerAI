package com.usf.itinerai.agent;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.trip.Trip;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// the agent's working copy of a trip: edits change only this copy, never the database. Locks, the removals flag
// and the half-length rule are enforced here, so the model can't get around them however it words a tool call
public class ItineraryDraft {

    private final Trip saved;
    // the saved trip's items by id, in schedule order
    private final Map<Long, ItineraryItem> savedItems = new LinkedHashMap<>();
    // the draft's items by id; an id is missing once its item is removed
    private final Map<Long, ItineraryItem> items;
    private final Set<Long> locked;
    private final boolean removalsAllowed;
    // the latest reason given for each changed item
    private final Map<Long, String> reasons = new HashMap<>();

    public ItineraryDraft(Trip saved, Set<Long> locked, boolean removalsAllowed) {
        if (saved == null || locked == null) {
            throw new IllegalArgumentException("saved and locked must not be null");
        }
        this.saved = saved;
        for (ItineraryItem item : saved.getItinerary().getItems()) {
            savedItems.put(item.getId(), item);
        }
        for (Long id : locked) {
            if (!savedItems.containsKey(id)) {
                throw new IllegalArgumentException("can't lock item " + id + ": it isn't in trip " + saved.getId());
            }
        }
        this.items = new LinkedHashMap<>(savedItems);
        this.locked = Set.copyOf(locked);
        this.removalsAllowed = removalsAllowed;
    }

    // starts a refine from the previous proposal; its changes were already accepted once, so locks don't apply to them
    public void applyPrevious(List<ProposedChange> changes) {
        Set<Long> seen = new HashSet<>();
        for (ProposedChange change : changes) {
            if (!seen.add(change.itemId())) {
                throw new IllegalArgumentException("item " + change.itemId() + " appears in more than one change");
            }
            ItineraryItem savedItem = change.findUnchangedIn(savedItems);
            switch (change.type()) {
                case RESCHEDULE -> items.put(change.itemId(), withTimes(savedItem, change.after()));
                case REMOVE -> items.remove(change.itemId());
            }
            if (change.reason() != null) {
                reasons.put(change.itemId(), change.reason());
            }
        }
    }

    public void move(long itemId, LocalTime start, LocalTime end, String reason) {
        ItineraryItem item = item(itemId);
        requireUnlocked(item);
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("end_time must be after start_time, on the same day");
        }
        Duration original = TimeRange.of(savedItems.get(itemId)).length();
        if (Duration.between(start, end).multipliedBy(2).compareTo(original) < 0) {
            throw new IllegalArgumentException("\"" + item.getTitle() + "\" can't be shorter than half its original "
                    + original.toMinutes() + " min, so it needs at least " + halfRoundedUp(original) + " min");
        }
        items.put(itemId, withTimes(item, new TimeRange(start, end)));
        reasons.put(itemId, reason);
    }

    public void remove(long itemId, String reason) {
        ItineraryItem item = item(itemId);
        if (!removalsAllowed) {
            throw new IllegalArgumentException("the traveler didn't allow removing items");
        }
        requireUnlocked(item);
        items.remove(itemId);
        reasons.put(itemId, reason);
    }

    // the item as it stands in the draft
    public ItineraryItem item(long itemId) {
        ItineraryItem item = items.get(itemId);
        if (item == null) {
            throw new IllegalArgumentException(savedItems.containsKey(itemId)
                    ? "item " + itemId + " was removed from the draft"
                    : "there is no item " + itemId + " in this trip");
        }
        return item;
    }

    public boolean isLocked(long itemId) {
        return locked.contains(itemId);
    }

    public boolean removalsAllowed() {
        return removalsAllowed;
    }

    public Trip saved() {
        return saved;
    }

    // a fresh Trip holding the draft's items, for the feasibility check
    public Trip toTrip() {
        Trip trip = new Trip(saved.getId(), saved.getName(), saved.getDestination(), saved.getDate(),
                saved.getTimeZone(), saved.getDefaultTravelMode());
        items.values().forEach(trip::addItem);
        return trip;
    }

    // the draft's items in schedule order
    public List<ItineraryItem> items() {
        return toTrip().getItinerary().getItems();
    }

    // every difference from the saved trip, in the saved schedule's order; this is exactly what Apply writes
    public List<ProposedChange> changes() {
        List<ProposedChange> changes = new ArrayList<>();
        for (ItineraryItem savedItem : savedItems.values()) {
            long id = savedItem.getId();
            TimeRange before = TimeRange.of(savedItem);
            ItineraryItem current = items.get(id);
            if (current == null) {
                changes.add(new ProposedChange(ChangeType.REMOVE, id, savedItem.getTitle(), before, null,
                        reasons.get(id)));
            } else if (!TimeRange.of(current).equals(before)) {
                changes.add(new ProposedChange(ChangeType.RESCHEDULE, id, savedItem.getTitle(), before,
                        TimeRange.of(current), reasons.get(id)));
            }
        }
        return changes;
    }

    // items are immutable, so new times mean a copy; the type switch mirrors the one in TripRepository
    static ItineraryItem withTimes(ItineraryItem item, TimeRange times) {
        return switch (item) {
            case Activity activity -> new Activity(activity.getId(), activity.getTitle(), activity.getLocation(),
                    times.start(), times.end(), activity.getNotes(), activity.getCategory());
            case Reservation reservation -> new Reservation(reservation.getId(), reservation.getTitle(),
                    reservation.getLocation(), times.start(), times.end(), reservation.getNotes(),
                    reservation.getConfirmationCode(), reservation.getPartySize());
            case Transportation transportation -> new Transportation(transportation.getId(), transportation.getTitle(),
                    transportation.getLocation(), times.start(), times.end(), transportation.getNotes(),
                    transportation.getOrigin(), transportation.getMode());
            default -> throw new IllegalArgumentException("unknown item type: " + item.getClass().getSimpleName());
        };
    }

    private void requireUnlocked(ItineraryItem item) {
        if (locked.contains(item.getId())) {
            throw new IllegalArgumentException("\"" + item.getTitle() + "\" is locked, so it can't change");
        }
    }

    private static long halfRoundedUp(Duration length) {
        return (length.toMinutes() + 1) / 2;
    }
}
