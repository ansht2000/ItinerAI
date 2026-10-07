package com.usf.itinerai.trip;

import com.usf.itinerai.itinerary.Activity;
import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.itinerary.Reservation;
import com.usf.itinerai.itinerary.Transportation;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// trips and their itinerary items, stored with plain SQL in the tables from db/migration;
// items are added, changed and removed one at a time, so the rest of a trip is never rewritten
@Repository
public class TripRepository {

    private final JdbcClient jdbc;

    public TripRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // saves a new trip along with any items it already has, and returns it as stored, with ids filled in
    @Transactional
    public Trip create(Trip trip) {
        if (trip.getId() != null) {
            throw new IllegalArgumentException("trip has already been saved");
        }
        long tripId = jdbc.sql("""
                        INSERT INTO trip (name, destination, trip_date, time_zone, default_travel_mode)
                        VALUES (:name, :destination, :date, :timeZone, :defaultTravelMode)
                        RETURNING id
                        """)
                .params(tripColumns(trip))
                .query(Long.class)
                .single();
        for (ItineraryItem item : trip.getItinerary().getItems()) {
            insertItem(tripId, item);
        }
        return findById(tripId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<Trip> findById(long id) {
        Optional<Trip> trip = jdbc.sql("SELECT * FROM trip WHERE id = :id")
                .param("id", id)
                .query(TripRepository::mapTrip)
                .optional();
        trip.ifPresent(found -> jdbc.sql("SELECT * FROM itinerary_item WHERE trip_id = :id ORDER BY start_time, id")
                .param("id", id)
                .query(rs -> {
                    found.addItem(mapItem(rs));
                }));
        return trip;
    }

    @Transactional(readOnly = true)
    public List<Trip> findAll() {
        List<Trip> trips = jdbc.sql("SELECT * FROM trip ORDER BY id")
                .query(TripRepository::mapTrip)
                .list();
        Map<Long, Trip> tripsById = new HashMap<>();
        trips.forEach(trip -> tripsById.put(trip.getId(), trip));
        jdbc.sql("SELECT * FROM itinerary_item ORDER BY start_time, id")
                .query(rs -> {
                    Trip trip = tripsById.get(rs.getLong("trip_id"));
                    // null for items of a trip created after the first query ran
                    if (trip != null) {
                        trip.addItem(mapItem(rs));
                    }
                });
        return trips;
    }

    // changes the trip's own fields; its items are changed through addItem, updateItem and deleteItem.
    // empty when there is no trip with that id
    @Transactional
    public Optional<Trip> update(Trip trip) {
        if (trip.getId() == null) {
            throw new IllegalArgumentException("trip has not been saved");
        }
        int updated = jdbc.sql("""
                        UPDATE trip
                        SET name = :name, destination = :destination, trip_date = :date,
                            time_zone = :timeZone, default_travel_mode = :defaultTravelMode
                        WHERE id = :id
                        """)
                .params(tripColumns(trip))
                .param("id", trip.getId())
                .update();
        return updated == 0 ? Optional.empty() : findById(trip.getId());
    }

    // the trip's items are deleted with it; false when there was no trip with that id
    public boolean deleteById(long id) {
        return jdbc.sql("DELETE FROM trip WHERE id = :id")
                .param("id", id)
                .update() > 0;
    }

    // returns the item as stored, with its id; empty when there is no trip with that id
    @Transactional
    public Optional<ItineraryItem> addItem(long tripId, ItineraryItem item) {
        if (item.getId() != null) {
            throw new IllegalArgumentException("item has already been saved");
        }
        boolean tripExists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM trip WHERE id = :id)")
                .param("id", tripId)
                .query(Boolean.class)
                .single();
        if (!tripExists) {
            return Optional.empty();
        }
        return findItem(tripId, insertItem(tripId, item));
    }

    // empty unless the item exists and belongs to that trip
    public Optional<ItineraryItem> findItem(long tripId, long itemId) {
        return jdbc.sql("SELECT * FROM itinerary_item WHERE id = :itemId AND trip_id = :tripId")
                .param("itemId", itemId)
                .param("tripId", tripId)
                .query((rs, rowNum) -> mapItem(rs))
                .optional();
    }

    // replaces every column of the stored item, so it can even change type (e.g. activity to reservation);
    // empty unless the item exists and belongs to that trip
    @Transactional
    public Optional<ItineraryItem> updateItem(long tripId, ItineraryItem item) {
        if (item.getId() == null) {
            throw new IllegalArgumentException("item has not been saved");
        }
        int updated = jdbc.sql("""
                        UPDATE itinerary_item
                        SET item_type = :itemType, title = :title, start_time = :startTime, end_time = :endTime,
                            notes = :notes,
                            location_name = :locationName, location_latitude = :locationLatitude,
                            location_longitude = :locationLongitude, location_open_time = :locationOpenTime,
                            location_close_time = :locationCloseTime,
                            category = :category, confirmation_code = :confirmationCode, party_size = :partySize,
                            origin_name = :originName, origin_latitude = :originLatitude,
                            origin_longitude = :originLongitude, origin_open_time = :originOpenTime,
                            origin_close_time = :originCloseTime, travel_mode = :travelMode
                        WHERE id = :id AND trip_id = :tripId
                        """)
                .params(itemColumns(item))
                .param("id", item.getId())
                .param("tripId", tripId)
                .update();
        return updated == 0 ? Optional.empty() : findItem(tripId, item.getId());
    }

    // false unless the item existed and belonged to that trip
    public boolean deleteItem(long tripId, long itemId) {
        return jdbc.sql("DELETE FROM itinerary_item WHERE id = :itemId AND trip_id = :tripId")
                .param("itemId", itemId)
                .param("tripId", tripId)
                .update() > 0;
    }

    private long insertItem(long tripId, ItineraryItem item) {
        return jdbc.sql("""
                        INSERT INTO itinerary_item (trip_id, item_type, title, start_time, end_time, notes,
                                                    location_name, location_latitude, location_longitude,
                                                    location_open_time, location_close_time,
                                                    category, confirmation_code, party_size,
                                                    origin_name, origin_latitude, origin_longitude,
                                                    origin_open_time, origin_close_time, travel_mode)
                        VALUES (:tripId, :itemType, :title, :startTime, :endTime, :notes,
                                :locationName, :locationLatitude, :locationLongitude,
                                :locationOpenTime, :locationCloseTime,
                                :category, :confirmationCode, :partySize,
                                :originName, :originLatitude, :originLongitude,
                                :originOpenTime, :originCloseTime, :travelMode)
                        RETURNING id
                        """)
                .params(itemColumns(item))
                .param("tripId", tripId)
                .query(Long.class)
                .single();
    }

    private static Map<String, Object> tripColumns(Trip trip) {
        return Map.of(
                "name", trip.getName(),
                "destination", trip.getDestination(),
                "date", trip.getDate(),
                "timeZone", trip.getTimeZone().getId(),
                "defaultTravelMode", trip.getDefaultTravelMode().name());
    }

    // a value for every item column; the columns of the other item types stay null
    private static Map<String, Object> itemColumns(ItineraryItem item) {
        Map<String, Object> columns = new HashMap<>();
        columns.put("title", item.getTitle());
        columns.put("startTime", item.getStartTime());
        columns.put("endTime", item.getEndTime());
        columns.put("notes", item.getNotes());
        putLocation(columns, "location", item.getLocation());
        columns.put("category", null);
        columns.put("confirmationCode", null);
        columns.put("partySize", null);
        putLocation(columns, "origin", null);
        columns.put("travelMode", null);
        switch (item) {
            case Activity activity -> {
                columns.put("itemType", "ACTIVITY");
                columns.put("category", activity.getCategory());
            }
            case Reservation reservation -> {
                columns.put("itemType", "RESERVATION");
                columns.put("confirmationCode", reservation.getConfirmationCode());
                columns.put("partySize", reservation.getPartySize());
            }
            case Transportation transportation -> {
                columns.put("itemType", "TRANSPORTATION");
                putLocation(columns, "origin", transportation.getOrigin());
                columns.put("travelMode", transportation.getMode().name());
            }
            default -> throw new IllegalArgumentException("unknown item type: " + item.getClass().getSimpleName());
        }
        return columns;
    }

    // prefix is "location" or "origin", matching the column names
    private static void putLocation(Map<String, Object> columns, String prefix, Location location) {
        if (location == null) {
            for (String field : List.of("Name", "Latitude", "Longitude", "OpenTime", "CloseTime")) {
                columns.put(prefix + field, null);
            }
            return;
        }
        columns.put(prefix + "Name", location.getName());
        columns.put(prefix + "Latitude", location.getLatitude());
        columns.put(prefix + "Longitude", location.getLongitude());
        columns.put(prefix + "OpenTime", location.getOpenTime());
        columns.put(prefix + "CloseTime", location.getCloseTime());
    }

    private static Trip mapTrip(ResultSet rs, int rowNum) throws SQLException {
        return new Trip(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("destination"),
                rs.getObject("trip_date", LocalDate.class),
                ZoneId.of(rs.getString("time_zone")),
                TravelMode.valueOf(rs.getString("default_travel_mode")));
    }

    private static ItineraryItem mapItem(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String title = rs.getString("title");
        Location location = mapLocation(rs, "location_");
        LocalTime startTime = rs.getObject("start_time", LocalTime.class);
        LocalTime endTime = rs.getObject("end_time", LocalTime.class);
        String notes = rs.getString("notes");
        String type = rs.getString("item_type");
        return switch (type) {
            case "ACTIVITY" -> new Activity(id, title, location, startTime, endTime, notes, rs.getString("category"));
            case "RESERVATION" -> new Reservation(id, title, location, startTime, endTime, notes,
                    rs.getString("confirmation_code"), rs.getObject("party_size", Integer.class));
            case "TRANSPORTATION" -> new Transportation(id, title, location, startTime, endTime, notes,
                    mapLocation(rs, "origin_"), TravelMode.valueOf(rs.getString("travel_mode")));
            default -> throw new IllegalStateException("unknown item_type in the database: " + type);
        };
    }

    // prefix is "location_" or "origin_", matching the column names
    private static Location mapLocation(ResultSet rs, String prefix) throws SQLException {
        return new Location(
                rs.getString(prefix + "name"),
                rs.getDouble(prefix + "latitude"),
                rs.getDouble(prefix + "longitude"),
                rs.getObject(prefix + "open_time", LocalTime.class),
                rs.getObject(prefix + "close_time", LocalTime.class));
    }
}
