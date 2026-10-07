package com.usf.itinerai.trip;

import com.usf.itinerai.TestcontainersConfiguration;
import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

// runs against a real Postgres in Docker; each test's rows are rolled back afterwards
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TripRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 1);
    private static final ZoneId PARIS_TIME = ZoneId.of("Europe/Paris");
    private static final Location HOTEL = new Location("Hotel", 48.8566, 2.3522, null, null);
    private static final Location LOUVRE =
            new Location("Louvre", 48.8606, 2.3376, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private static final Location BISTRO = new Location("Bistro", 48.8530, 2.3499, LocalTime.of(12, 0), null);

    @Autowired
    private TripRepository trips;

    @Test
    void createSavesTheTripAndEveryItemType() {
        Trip trip = parisTrip();
        Transportation metro = new Transportation("Metro", LOUVRE, at(9, 0), at(9, 30), "line 1", HOTEL,
                TravelMode.TRANSIT);
        Activity museum = museum();
        Reservation lunch = new Reservation("Lunch", BISTRO, at(12, 30), at(13, 30), null, "ABC123", 2);
        trip.addItem(museum);
        trip.addItem(lunch);
        trip.addItem(metro);

        Trip saved = trips.create(trip);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Paris day");
        assertThat(saved.getDestination()).isEqualTo("Paris");
        assertThat(saved.getDate()).isEqualTo(DATE);
        assertThat(saved.getTimeZone()).isEqualTo(PARIS_TIME);
        assertThat(saved.getDefaultTravelMode()).isEqualTo(TravelMode.WALK);
        assertThat(saved.getItinerary().getItems())
                .allSatisfy(item -> assertThat(item.getId()).isNotNull())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
                .containsExactly(metro, museum, lunch);
    }

    @Test
    void findByIdReadsBackWhatWasCreated() {
        Trip trip = parisTrip();
        trip.addItem(museum());
        Trip saved = trips.create(trip);

        assertThat(trips.findById(saved.getId())).get().usingRecursiveComparison().isEqualTo(saved);
    }

    @Test
    void itemsComeBackInStartTimeOrder() {
        long tripId = trips.create(parisTrip()).getId();
        trips.addItem(tripId, new Activity("Dinner", BISTRO, at(19, 0), at(21, 0), null, null));
        trips.addItem(tripId, museum());

        assertThat(trips.findById(tripId).orElseThrow().getItinerary().getItems())
                .extracting(ItineraryItem::getTitle)
                .containsExactly("Museum", "Dinner");
    }

    @Test
    void findAllReturnsEachTripWithItsOwnItems() {
        Trip paris = parisTrip();
        paris.addItem(museum());
        long parisId = trips.create(paris).getId();
        Trip rome = new Trip("Rome day", "Rome", DATE, ZoneId.of("Europe/Rome"), TravelMode.TRANSIT);
        rome.addItem(new Activity("Colosseum", new Location("Colosseum", 41.8902, 12.4922, null, null),
                at(10, 0), at(12, 0), null, null));
        long romeId = trips.create(rome).getId();

        assertThat(trips.findAll())
                .filteredOn(trip -> trip.getId() == parisId || trip.getId() == romeId)
                .extracting(trip -> trip.getItinerary().getItems().getFirst().getTitle())
                .containsExactly("Museum", "Colosseum");
    }

    @Test
    void updateChangesTheTripAndKeepsItsItems() {
        Trip trip = parisTrip();
        trip.addItem(museum());
        long tripId = trips.create(trip).getId();

        Trip updated = trips.update(new Trip(tripId, "Paris weekend", "Paris", DATE.plusDays(1), PARIS_TIME,
                TravelMode.DRIVE)).orElseThrow();

        assertThat(updated.getName()).isEqualTo("Paris weekend");
        assertThat(updated.getDate()).isEqualTo(DATE.plusDays(1));
        assertThat(updated.getDefaultTravelMode()).isEqualTo(TravelMode.DRIVE);
        assertThat(updated.getItinerary().getItems()).extracting(ItineraryItem::getTitle).containsExactly("Museum");
    }

    @Test
    void deleteRemovesTheTripAndItsItems() {
        Trip trip = parisTrip();
        trip.addItem(museum());
        Trip saved = trips.create(trip);
        long itemId = saved.getItinerary().getItems().getFirst().getId();

        assertThat(trips.deleteById(saved.getId())).isTrue();

        assertThat(trips.findById(saved.getId())).isEmpty();
        assertThat(trips.findItem(saved.getId(), itemId)).isEmpty();
        assertThat(trips.deleteById(saved.getId())).isFalse();
    }

    @Test
    void addItemReturnsTheSavedItemWithItsId() {
        long tripId = trips.create(parisTrip()).getId();
        Activity museum = museum();

        ItineraryItem saved = trips.addItem(tripId, museum).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved).usingRecursiveComparison().ignoringFields("id").isEqualTo(museum);
        assertThat(trips.findItem(tripId, saved.getId())).get().usingRecursiveComparison().isEqualTo(saved);
    }

    @Test
    void updateItemCanChangeTheItemType() {
        long tripId = trips.create(parisTrip()).getId();
        long itemId = trips.addItem(tripId, museum()).orElseThrow().getId();
        Transportation taxi = new Transportation(itemId, "Taxi", LOUVRE, at(9, 30), at(10, 0), null, HOTEL,
                TravelMode.DRIVE);

        ItineraryItem updated = trips.updateItem(tripId, taxi).orElseThrow();

        assertThat(updated).usingRecursiveComparison().isEqualTo(taxi);
        assertThat(trips.findItem(tripId, itemId)).get().isInstanceOf(Transportation.class);
    }

    @Test
    void deleteItemRemovesOnlyThatItem() {
        long tripId = trips.create(parisTrip()).getId();
        long museumId = trips.addItem(tripId, museum()).orElseThrow().getId();
        trips.addItem(tripId, new Activity("Dinner", BISTRO, at(19, 0), at(21, 0), null, null));

        assertThat(trips.deleteItem(tripId, museumId)).isTrue();

        assertThat(trips.findById(tripId).orElseThrow().getItinerary().getItems())
                .extracting(ItineraryItem::getTitle)
                .containsExactly("Dinner");
        assertThat(trips.deleteItem(tripId, museumId)).isFalse();
    }

    @Test
    void itemsCannotBeReachedThroughAnotherTrip() {
        long parisId = trips.create(parisTrip()).getId();
        long otherTripId = trips.create(parisTrip()).getId();
        long museumId = trips.addItem(parisId, museum()).orElseThrow().getId();

        assertThat(trips.findItem(otherTripId, museumId)).isEmpty();
        assertThat(trips.updateItem(otherTripId, new Activity(museumId, "Changed", LOUVRE, at(10, 0), at(11, 0),
                null, null))).isEmpty();
        assertThat(trips.deleteItem(otherTripId, museumId)).isFalse();
        assertThat(trips.findItem(parisId, museumId)).get().extracting(ItineraryItem::getTitle).isEqualTo("Museum");
    }

    @Test
    void unknownTripsAreReportedAsEmpty() {
        long missingId = -1;

        assertThat(trips.findById(missingId)).isEmpty();
        assertThat(trips.update(new Trip(missingId, "Paris day", "Paris", DATE, PARIS_TIME, TravelMode.WALK)))
                .isEmpty();
        assertThat(trips.addItem(missingId, museum())).isEmpty();
        assertThat(trips.deleteById(missingId)).isFalse();
    }

    private static Trip parisTrip() {
        return new Trip("Paris day", "Paris", DATE, PARIS_TIME, TravelMode.WALK);
    }

    private static Activity museum() {
        return new Activity("Museum", LOUVRE, at(10, 0), at(12, 0), "book tickets ahead", "art");
    }

    private static LocalTime at(int hour, int minute) {
        return LocalTime.of(hour, minute);
    }
}
