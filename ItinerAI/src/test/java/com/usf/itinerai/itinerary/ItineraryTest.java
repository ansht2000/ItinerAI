package com.usf.itinerai.itinerary;

import com.usf.itinerai.location.Location;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItineraryTest {

    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);

    @Test
    void constructorSortsByStartTime() {
        Itinerary itinerary = new Itinerary(List.of(activity("Lunch", 12), activity("Breakfast", 8),
                activity("Museum", 10)));

        assertThat(titles(itinerary)).containsExactly("Breakfast", "Museum", "Lunch");
    }

    @Test
    void constructorKeepsGivenOrderForTiedStartTimes() {
        Itinerary itinerary = new Itinerary(List.of(activity("Lunch", 12), activity("Tour", 10),
                activity("Gallery", 10)));

        assertThat(titles(itinerary)).containsExactly("Tour", "Gallery", "Lunch");
    }

    @Test
    void constructorCopiesTheGivenList() {
        List<ItineraryItem> items = new ArrayList<>(List.of(activity("Museum", 10)));
        Itinerary itinerary = new Itinerary(items);

        items.clear();

        assertThat(titles(itinerary)).containsExactly("Museum");
    }

    @Test
    void noArgConstructorStartsEmpty() {
        assertThat(new Itinerary().getItems()).isEmpty();
    }

    @Test
    void addItemInsertsAtStart() {
        Itinerary itinerary = new Itinerary(List.of(activity("Museum", 10), activity("Lunch", 12)));

        itinerary.addItem(activity("Breakfast", 8));

        assertThat(titles(itinerary)).containsExactly("Breakfast", "Museum", "Lunch");
    }

    @Test
    void addItemInsertsInMiddle() {
        Itinerary itinerary = new Itinerary(List.of(activity("Breakfast", 8), activity("Lunch", 12)));

        itinerary.addItem(activity("Museum", 10));

        assertThat(titles(itinerary)).containsExactly("Breakfast", "Museum", "Lunch");
    }

    @Test
    void addItemInsertsAtEnd() {
        Itinerary itinerary = new Itinerary(List.of(activity("Breakfast", 8), activity("Museum", 10)));

        itinerary.addItem(activity("Dinner", 19));

        assertThat(titles(itinerary)).containsExactly("Breakfast", "Museum", "Dinner");
    }

    @Test
    void addItemGoesAfterItemsWithSameStartTime() {
        Itinerary itinerary = new Itinerary(List.of(activity("Tour", 10), activity("Gallery", 10),
                activity("Lunch", 12)));

        itinerary.addItem(activity("Cafe", 10));

        assertThat(titles(itinerary)).containsExactly("Tour", "Gallery", "Cafe", "Lunch");
    }

    @Test
    void getItemsCannotBeModified() {
        Itinerary itinerary = new Itinerary(List.of(activity("Museum", 10)));

        assertThatThrownBy(() -> itinerary.getItems().add(activity("Lunch", 12)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getItemsReflectsLaterAdds() {
        Itinerary itinerary = new Itinerary();
        List<ItineraryItem> view = itinerary.getItems();

        itinerary.addItem(activity("Museum", 10));

        assertThat(view).extracting(ItineraryItem::getTitle).containsExactly("Museum");
    }

    @Test
    void nullListIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Itinerary(null))
                .withMessage("initialItems must not be null");
    }

    @Test
    void nullItemInListIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Itinerary(Arrays.asList(activity("Museum", 10), null)))
                .withMessage("item must not be null");
    }

    @Test
    void addingNullItemIsRejected() {
        Itinerary itinerary = new Itinerary();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> itinerary.addItem(null))
                .withMessage("item must not be null");
    }

    private Activity activity(String title, int startHour) {
        return new Activity(title, LOUVRE, LocalTime.of(startHour, 0), LocalTime.of(startHour, 30), null, null);
    }

    private List<String> titles(Itinerary itinerary) {
        return itinerary.getItems().stream().map(ItineraryItem::getTitle).toList();
    }
}
