package com.usf.itinerai.location;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocationTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime FIVE = LocalTime.of(17, 0);

    @Test
    void fullHoursAreKept() {
        Location location = locationWithHours(NINE, FIVE);

        assertThat(location.getOpenTime()).isEqualTo(NINE);
        assertThat(location.getCloseTime()).isEqualTo(FIVE);
    }

    @Test
    void hoursAreOptional() {
        Location location = locationWithHours(null, null);

        assertThat(location.getOpenTime()).isNull();
        assertThat(location.getCloseTime()).isNull();
    }

    @Test
    void openTimeAloneIsAccepted() {
        Location location = locationWithHours(NINE, null);

        assertThat(location.getOpenTime()).isEqualTo(NINE);
        assertThat(location.getCloseTime()).isNull();
    }

    @Test
    void closeTimeAloneIsAccepted() {
        Location location = locationWithHours(null, FIVE);

        assertThat(location.getOpenTime()).isNull();
        assertThat(location.getCloseTime()).isEqualTo(FIVE);
    }

    // overnight hours are a known limitation for Milestone 1
    @Test
    void closeBeforeOpenIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> locationWithHours(FIVE, NINE))
                .withMessage("closeTime must be after openTime");
    }

    @Test
    void closeEqualToOpenIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> locationWithHours(NINE, NINE))
                .withMessage("closeTime must be after openTime");
    }

    private Location locationWithHours(LocalTime openTime, LocalTime closeTime) {
        return new Location("Louvre", 48.8606, 2.3376, openTime, closeTime);
    }
}
