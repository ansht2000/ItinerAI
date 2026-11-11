package com.usf.itinerai.agent;

import com.usf.itinerai.itinerary.ItineraryItem;

import java.util.Map;

// one difference between a proposal and the saved trip; after is null for REMOVE, and reason comes from the agent
public record ProposedChange(ChangeType type, long itemId, String title, TimeRange before, TimeRange after,
                             String reason) {

    public ProposedChange {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (before == null) {
            throw new IllegalArgumentException("before must not be null");
        }
        if (type == ChangeType.RESCHEDULE && after == null) {
            throw new IllegalArgumentException("a RESCHEDULE change needs after times");
        }
        if (type == ChangeType.REMOVE && after != null) {
            throw new IllegalArgumentException("a REMOVE change has no after times");
        }
    }

    // the saved item this change was made from; throws when the item is gone or its times changed since,
    // so a proposal is never applied on top of edits it didn't know about
    ItineraryItem findUnchangedIn(Map<Long, ItineraryItem> savedItems) {
        ItineraryItem saved = savedItems.get(itemId);
        if (saved == null) {
            throw new StaleProposalException("item " + itemId + " is no longer in the trip");
        }
        if (!TimeRange.of(saved).equals(before)) {
            throw new StaleProposalException("\"" + saved.getTitle() + "\" changed after the proposal was made");
        }
        return saved;
    }
}
