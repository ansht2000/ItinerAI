package com.usf.itinerai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// applies the Flyway migrations to a real Postgres in Docker and checks the rules the schema enforces
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional // each test's rows are rolled back afterwards
class SchemaMigrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void storesOneOfEachItemType() {
        long tripId = insertTrip();

        insertItem(tripId, "ACTIVITY", "category", "'art'");
        insertItem(tripId, "RESERVATION", "confirmation_code, party_size", "'ABC123', 2");
        insertItem(tripId, "TRANSPORTATION", "origin_name, origin_latitude, origin_longitude, travel_mode",
                "'Hotel', 48.8566, 2.3522, 'TRANSIT'");

        assertThat(itemCount(tripId)).isEqualTo(3);
    }

    @Test
    void deletingATripDeletesItsItems() {
        long tripId = insertTrip();
        insertItem(tripId, "ACTIVITY", "category", "NULL");

        jdbc.sql("DELETE FROM trip WHERE id = :id").param("id", tripId).update();

        assertThat(itemCount(tripId)).isZero();
    }

    @Test
    void rejectsEndTimeBeforeStartTime() {
        long tripId = insertTrip();

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO itinerary_item (trip_id, item_type, title, start_time, end_time,
                                            location_name, location_latitude, location_longitude)
                VALUES (:tripId, 'ACTIVITY', 'Louvre', '11:00', '10:00', 'Louvre', 48.8606, 2.3376)
                """).param("tripId", tripId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("item_time_range_valid");
    }

    @Test
    void rejectsUnknownItemType() {
        long tripId = insertTrip();

        assertThatThrownBy(() -> insertItem(tripId, "TOUR", "category", "NULL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("item_type_valid");
    }

    @Test
    void rejectsTransportationWithoutOrigin() {
        long tripId = insertTrip();

        assertThatThrownBy(() -> insertItem(tripId, "TRANSPORTATION", "travel_mode", "'WALK'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("item_transportation_columns");
    }

    @Test
    void rejectsTransportationColumnsOnOtherTypes() {
        long tripId = insertTrip();

        assertThatThrownBy(() -> insertItem(tripId, "ACTIVITY", "travel_mode", "'WALK'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("item_transportation_columns");
    }

    private long insertTrip() {
        return jdbc.sql("""
                INSERT INTO trip (name, destination, trip_date, time_zone, default_travel_mode)
                VALUES ('Day in Paris', 'Paris', '2026-10-08', 'Europe/Paris', 'WALK')
                RETURNING id
                """).query(Long.class).single();
    }

    // fills the columns every item has, plus the given type-specific columns and their SQL values
    private void insertItem(long tripId, String type, String extraColumns, String extraValues) {
        jdbc.sql("""
                INSERT INTO itinerary_item (trip_id, item_type, title, start_time, end_time,
                                            location_name, location_latitude, location_longitude, %s)
                VALUES (:tripId, :type, 'Item', '10:00', '11:00', 'Louvre', 48.8606, 2.3376, %s)
                """.formatted(extraColumns, extraValues))
                .param("tripId", tripId)
                .param("type", type)
                .update();
    }

    private long itemCount(long tripId) {
        return jdbc.sql("SELECT count(*) FROM itinerary_item WHERE trip_id = :tripId")
                .param("tripId", tripId)
                .query(Long.class)
                .single();
    }
}
