package com.usf.itinerai.agent;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import com.usf.itinerai.trip.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class ItineraryDraftTest {

    private static final Location LOUVRE =
            new Location("Louvre", 48.8606, 2.3376, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private static final Location CONSULAT = new Location("Le Consulat", 48.8866, 2.3404, null, null);
    private static final Location EIFFEL_TOWER = new Location("Eiffel Tower", 48.8584, 2.2945, null, null);

    private static final long LOUVRE_ID = 1;
    private static final long LUNCH_ID = 2;
    private static final long METRO_ID = 3;

    private Trip trip;

    @BeforeEach
    void setUp() {
        trip = new Trip(42L, "Paris day", "Paris", LocalDate.of(2026, 11, 1), ZoneId.of("Europe/Paris"),
                TravelMode.WALK);
        trip.addItem(new Activity(LOUVRE_ID, "Louvre", LOUVRE, time("10:00"), time("12:00"), "Book ahead", "art"));
        trip.addItem(new Reservation(LUNCH_ID, "Lunch", CONSULAT, time("12:20"), time("13:30"), null, "ABC123", 2));
        trip.addItem(new Transportation(METRO_ID, "Metro", EIFFEL_TOWER, time("18:30"), time("19:15"), null,
                CONSULAT, TravelMode.TRANSIT));
    }

    @Test
    void newDraftHasNoChanges() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        assertThat(draft.changes()).isEmpty();
        assertThat(draft.items()).extracting(ItineraryItem::getId).containsExactly(LOUVRE_ID, LUNCH_ID, METRO_ID);
    }

    @Test
    void movingAnItemIsAReschedule() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        draft.move(LOUVRE_ID, time("09:20"), time("11:20"), "Leaves time to walk to lunch.");

        assertThat(draft.changes()).containsExactly(new ProposedChange(ChangeType.RESCHEDULE, LOUVRE_ID, "Louvre",
                range("10:00", "12:00"), range("09:20", "11:20"), "Leaves time to walk to lunch."));
        assertThat(draft.item(LOUVRE_ID).getStartTime()).isEqualTo(time("09:20"));
    }

    @Test
    void moveKeepsEverythingElseAboutTheItem() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        draft.move(LUNCH_ID, time("13:00"), time("14:00"), "Later lunch.");
        draft.move(METRO_ID, time("18:00"), time("18:45"), "Earlier train.");

        Reservation lunch = (Reservation) draft.item(LUNCH_ID);
        assertThat(lunch.getConfirmationCode()).isEqualTo("ABC123");
        assertThat(lunch.getPartySize()).isEqualTo(2);
        Transportation metro = (Transportation) draft.item(METRO_ID);
        assertThat(metro.getOrigin()).isSameAs(CONSULAT);
        assertThat(metro.getMode()).isEqualTo(TravelMode.TRANSIT);
        Activity louvre = (Activity) draft.item(LOUVRE_ID);
        assertThat(louvre.getCategory()).isEqualTo("art");
    }

    @Test
    void movingBackToTheSavedTimesIsNoChange() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        draft.move(LOUVRE_ID, time("09:00"), time("11:00"), "Earlier.");
        draft.move(LOUVRE_ID, time("10:00"), time("12:00"), "Back again.");

        assertThat(draft.changes()).isEmpty();
    }

    @Test
    void lockedItemsCantMoveOrBeRemoved() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(LUNCH_ID), true);

        assertThatThrownBy(() -> draft.move(LUNCH_ID, time("13:00"), time("14:00"), "Later."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\"Lunch\" is locked, so it can't change");
        assertThatThrownBy(() -> draft.remove(LUNCH_ID, "Too much."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(draft.changes()).isEmpty();
    }

    @Test
    void anItemCanShrinkToHalfButNoFurther() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        draft.move(LOUVRE_ID, time("10:00"), time("11:00"), "A shorter visit.");

        assertThatThrownBy(() -> draft.move(LOUVRE_ID, time("10:00"), time("10:59"), "Even shorter."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\"Louvre\" can't be shorter than half its original 120 min, so it needs at least 60 min");
        assertThat(draft.item(LOUVRE_ID).getEndTime()).isEqualTo(time("11:00"));
    }

    @Test
    void endMustBeAfterStart() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        assertThatThrownBy(() -> draft.move(LOUVRE_ID, time("12:00"), time("11:00"), "Backwards."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("end_time must be after start_time, on the same day");
    }

    @Test
    void unknownItemIsRejected() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        assertThatThrownBy(() -> draft.move(99, time("10:00"), time("11:00"), "?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("there is no item 99 in this trip");
    }

    @Test
    void removalIsAChangeWithNoAfterTimes() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), true);

        draft.remove(METRO_ID, "Not needed.");

        assertThat(draft.changes()).containsExactly(new ProposedChange(ChangeType.REMOVE, METRO_ID, "Metro",
                range("18:30", "19:15"), null, "Not needed."));
        assertThat(draft.items()).extracting(ItineraryItem::getId).containsExactly(LOUVRE_ID, LUNCH_ID);
        assertThatThrownBy(() -> draft.move(METRO_ID, time("18:00"), time("18:45"), "?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("item 3 was removed from the draft");
    }

    @Test
    void removalsNeedPermission() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        assertThatThrownBy(() -> draft.remove(METRO_ID, "Not needed."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the traveler didn't allow removing items");
    }

    @Test
    void lockingAnItemThatIsntInTheTripIsRejected() {
        assertThatThrownBy(() -> new ItineraryDraft(trip, Set.of(99L), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("can't lock item 99: it isn't in trip 42");
    }

    @Test
    void previousChangesStartTheDraftEvenForLockedItems() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(LOUVRE_ID), false);

        draft.applyPrevious(List.of(
                new ProposedChange(ChangeType.RESCHEDULE, LOUVRE_ID, "Louvre", range("10:00", "12:00"),
                        range("09:20", "11:20"), "Leaves time to walk to lunch."),
                new ProposedChange(ChangeType.REMOVE, METRO_ID, "Metro", range("18:30", "19:15"), null,
                        "Not needed.")));

        assertThat(draft.changes()).extracting(ProposedChange::type, ProposedChange::itemId, ProposedChange::reason)
                .containsExactly(
                        tuple(ChangeType.RESCHEDULE, LOUVRE_ID, "Leaves time to walk to lunch."),
                        tuple(ChangeType.REMOVE, METRO_ID, "Not needed."));
    }

    @Test
    void previousChangeThatNoLongerMatchesIsStale() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        assertThatThrownBy(() -> draft.applyPrevious(List.of(new ProposedChange(ChangeType.RESCHEDULE, LOUVRE_ID,
                "Louvre", range("08:00", "10:00"), range("09:00", "11:00"), "Earlier."))))
                .isInstanceOf(StaleProposalException.class)
                .hasMessage("\"Louvre\" changed after the proposal was made");
    }

    @Test
    void previousChangeToAnItemThatIsGoneIsStale() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        assertThatThrownBy(() -> draft.applyPrevious(List.of(new ProposedChange(ChangeType.REMOVE, 99, "Gone",
                range("08:00", "10:00"), null, "?"))))
                .isInstanceOf(StaleProposalException.class)
                .hasMessage("item 99 is no longer in the trip");
    }

    @Test
    void sameItemInTwoPreviousChangesIsRejected() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);
        ProposedChange earlier = new ProposedChange(ChangeType.RESCHEDULE, LOUVRE_ID, "Louvre",
                range("10:00", "12:00"), range("09:00", "11:00"), "Earlier.");

        assertThatThrownBy(() -> draft.applyPrevious(List.of(earlier, earlier)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("item 1 appears in more than one change");
    }

    @Test
    void draftTripKeepsTheSavedTripsDetailsAndSortsItems() {
        ItineraryDraft draft = new ItineraryDraft(trip, Set.of(), false);

        draft.move(METRO_ID, time("08:00"), time("08:45"), "Early train.");
        Trip draftTrip = draft.toTrip();

        assertThat(draftTrip.getId()).isEqualTo(42L);
        assertThat(draftTrip.getDefaultTravelMode()).isEqualTo(TravelMode.WALK);
        assertThat(draftTrip.getItinerary().getItems()).extracting(ItineraryItem::getId)
                .containsExactly(METRO_ID, LOUVRE_ID, LUNCH_ID);
        // the saved trip itself is untouched
        assertThat(trip.getItinerary().getItems()).extracting(ItineraryItem::getId)
                .containsExactly(LOUVRE_ID, LUNCH_ID, METRO_ID);
    }

    private static LocalTime time(String value) {
        return LocalTime.parse(value);
    }

    private static TimeRange range(String start, String end) {
        return new TimeRange(time(start), time(end));
    }
}
