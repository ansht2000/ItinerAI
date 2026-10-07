package com.usf.itinerai.web.dto;

import com.usf.itinerai.itinerary.TravelMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ItemRequestTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void typeFieldSelectsSubtype() {
        ItemRequest item = mapper.readValue("""
                {"type": "transportation", "title": "Metro to the Louvre",
                 "startTime": "09:00", "endTime": "09:30", "mode": "TRANSIT",
                 "origin": {"name": "Hotel", "latitude": 48.8566, "longitude": 2.3522},
                 "destination": {"name": "Louvre", "latitude": 48.8606, "longitude": 2.3376,
                                 "openTime": "09:00", "closeTime": "18:00"}}
                """, ItemRequest.class);

        assertThat(item).isInstanceOfSatisfying(TransportationRequest.class, transportation -> {
            assertThat(transportation.mode()).isEqualTo(TravelMode.TRANSIT);
            assertThat(transportation.startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(transportation.destination().latitude()).isEqualTo(48.8606);
            assertThat(transportation.destination().closeTime()).isEqualTo(LocalTime.of(18, 0));
        });
        assertThat(validator.validate(item)).isEmpty();
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThat(violationPaths("""
                {"type": "activity", "title": "Louvre", "startTime": "12:00", "endTime": "11:00",
                 "location": {"name": "Louvre", "latitude": 48.8606, "longitude": 2.3376}}
                """)).containsExactly("timeRangeValid");
    }

    @Test
    void locationNeedsBothCoordinates() {
        assertThat(violationPaths("""
                {"type": "reservation", "title": "Lunch", "startTime": "12:00", "endTime": "13:00",
                 "location": {"name": "Somewhere", "latitude": 48.86}}
                """)).containsExactly("location.longitude");
    }

    @Test
    void coordinatesMustBeInRange() {
        assertThat(violationPaths("""
                {"type": "activity", "title": "Louvre", "startTime": "10:00", "endTime": "11:00",
                 "location": {"name": "Louvre", "latitude": 95.0, "longitude": 2.3376}}
                """)).containsExactly("location.latitude");
    }

    @Test
    void closeTimeMustBeAfterOpenTime() {
        assertThat(violationPaths("""
                {"type": "activity", "title": "Louvre", "startTime": "10:00", "endTime": "11:00",
                 "location": {"name": "Louvre", "latitude": 48.8606, "longitude": 2.3376,
                              "openTime": "18:00", "closeTime": "09:00"}}
                """)).containsExactly("location.hoursValid");
    }

    @Test
    void requiredFieldsAreReported() {
        assertThat(violationPaths("""
                {"type": "activity", "title": " ", "startTime": "10:00"}
                """)).containsExactlyInAnyOrder("title", "endTime", "location");
    }

    private Set<String> violationPaths(String json) {
        ItemRequest item = mapper.readValue(json, ItemRequest.class);
        return validator.validate(item).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
