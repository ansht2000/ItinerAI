package com.usf.itinerai.trip;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TripTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 1);
    private static final ZoneId PARIS_TIME = ZoneId.of("Europe/Paris");
    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);

    @Test
    void gettersReturnConstructorValues() {
        Trip trip = new Trip("Paris day", "Paris", DATE, PARIS_TIME, TravelMode.TRANSIT);

        assertThat(trip.getName()).isEqualTo("Paris day");
        assertThat(trip.getDestination()).isEqualTo("Paris");
        assertThat(trip.getDate()).isEqualTo(DATE);
        assertThat(trip.getTimeZone()).isEqualTo(PARIS_TIME);
        assertThat(trip.getDefaultTravelMode()).isEqualTo(TravelMode.TRANSIT);
    }

    @Test
    void itineraryStartsEmpty() {
        assertThat(parisTrip().getItinerary().getItems()).isEmpty();
    }

    @Test
    void addItemKeepsItinerarySorted() {
        Trip trip = parisTrip();

        trip.addItem(activity("Lunch", 12));
        trip.addItem(activity("Museum", 10));

        assertThat(trip.getItinerary().getItems()).extracting(ItineraryItem::getTitle)
                .containsExactly("Museum", "Lunch");
    }

    @Test
    void addingNullItemIsRejected() {
        Trip trip = parisTrip();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> trip.addItem(null))
                .withMessage("item must not be null");
    }

    @Test
    void nullNameIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Trip(null, "Paris", DATE, PARIS_TIME, TravelMode.WALK))
                .withMessage("name must not be null or blank");
    }

    @Test
    void blankNameIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Trip(" ", "Paris", DATE, PARIS_TIME, TravelMode.WALK))
                .withMessage("name must not be null or blank");
    }

    @Test
    void nullDestinationIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Trip("Paris day", null, DATE, PARIS_TIME, TravelMode.WALK))
                .withMessage("destination must not be null or blank");
    }

    @Test
    void blankDestinationIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Trip("Paris day", "", DATE, PARIS_TIME, TravelMode.WALK))
                .withMessage("destination must not be null or blank");
    }

    @Test
    void nullDateIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Trip("Paris day", "Paris", null, PARIS_TIME, TravelMode.WALK))
                .withMessage("date must not be null");
    }

    @Test
    void nullTimeZoneIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Trip("Paris day", "Paris", DATE, null, TravelMode.WALK))
                .withMessage("timeZone must not be null");
    }

    @Test
    void nullDefaultTravelModeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Trip("Paris day", "Paris", DATE, PARIS_TIME, null))
                .withMessage("defaultTravelMode must not be null");
    }

    private Trip parisTrip() {
        return new Trip("Paris day", "Paris", DATE, PARIS_TIME, TravelMode.TRANSIT);
    }

    private Activity activity(String title, int startHour) {
        return new Activity(title, LOUVRE, LocalTime.of(startHour, 0), LocalTime.of(startHour + 1, 0), null, null);
    }
}
