package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ItineraryItemTest {

    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);
    private static final Location HOTEL = new Location("Hotel", 48.8566, 2.3522, null, null);
    private static final LocalTime TEN = LocalTime.of(10, 0);
    private static final LocalTime ELEVEN = LocalTime.of(11, 0);

    @Test
    void newItemsHaveNoId() {
        assertThat(new Activity("Louvre", LOUVRE, TEN, ELEVEN, null, null).getId()).isNull();
        assertThat(new Reservation("Lunch", LOUVRE, TEN, ELEVEN, null, null, null).getId()).isNull();
        assertThat(new Transportation("Metro", LOUVRE, TEN, ELEVEN, null, HOTEL, TravelMode.TRANSIT).getId())
                .isNull();
    }

    @Test
    void activityKeepsItsIdAndFields() {
        Activity activity = new Activity(1L, "Louvre", LOUVRE, TEN, ELEVEN, "skip the line", "art");

        assertThat(activity.getId()).isEqualTo(1L);
        assertThat(activity.getTitle()).isEqualTo("Louvre");
        assertThat(activity.getNotes()).isEqualTo("skip the line");
        assertThat(activity.getCategory()).isEqualTo("art");
    }

    @Test
    void reservationKeepsItsIdAndFields() {
        Reservation reservation = new Reservation(2L, "Lunch", LOUVRE, TEN, ELEVEN, null, "ABC123", 2);

        assertThat(reservation.getId()).isEqualTo(2L);
        assertThat(reservation.getConfirmationCode()).isEqualTo("ABC123");
        assertThat(reservation.getPartySize()).isEqualTo(2);
    }

    @Test
    void transportationKeepsItsIdAndFields() {
        Transportation metro = new Transportation(3L, "Metro", LOUVRE, TEN, ELEVEN, null, HOTEL, TravelMode.TRANSIT);

        assertThat(metro.getId()).isEqualTo(3L);
        assertThat(metro.getLocation()).isSameAs(LOUVRE);
        assertThat(metro.getOrigin()).isSameAs(HOTEL);
        assertThat(metro.getMode()).isEqualTo(TravelMode.TRANSIT);
    }

    @Test
    void constructorsWithAnIdStillValidate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Activity(1L, "Louvre", LOUVRE, ELEVEN, TEN, null, null))
                .withMessage("endTime must be after startTime");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Reservation(2L, "Lunch", LOUVRE, TEN, ELEVEN, null, null, 0))
                .withMessage("partySize must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transportation(3L, "Metro", LOUVRE, TEN, ELEVEN, null, null, TravelMode.WALK))
                .withMessage("origin must not be null");
    }
}
