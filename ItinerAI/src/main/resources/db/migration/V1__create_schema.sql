-- Trips and their itinerary items.
-- Strings are TEXT rather than VARCHAR(n): Postgres stores both the same way, and TEXT never rejects long user input.

-- a trip covers a single day
CREATE TABLE trip (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                TEXT NOT NULL,
    destination         TEXT NOT NULL,
    trip_date           DATE NOT NULL,
    time_zone           TEXT NOT NULL, -- IANA id, e.g. Europe/Paris
    default_travel_mode TEXT NOT NULL,

    CONSTRAINT trip_travel_mode_valid
        CHECK (default_travel_mode IN ('WALK', 'DRIVE', 'TRANSIT', 'BICYCLE'))
);

-- Activities, reservations and transportation share this table: item_type says which one a row is,
-- and each type fills only its own columns.
-- An item's location is stored in its own columns rather than a separate table. For transportation,
-- location_* is the destination and origin_* is where it leaves from.
CREATE TABLE itinerary_item (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id             BIGINT NOT NULL REFERENCES trip (id) ON DELETE CASCADE,
    item_type           TEXT   NOT NULL,
    title               TEXT   NOT NULL,
    start_time          TIME   NOT NULL,
    end_time            TIME   NOT NULL,
    notes               TEXT,

    location_name       TEXT             NOT NULL,
    location_latitude   DOUBLE PRECISION NOT NULL,
    location_longitude  DOUBLE PRECISION NOT NULL,
    location_open_time  TIME,
    location_close_time TIME,

    -- activity
    category            TEXT,

    -- reservation
    confirmation_code   TEXT,
    party_size          INTEGER,

    -- transportation
    origin_name         TEXT,
    origin_latitude     DOUBLE PRECISION,
    origin_longitude    DOUBLE PRECISION,
    origin_open_time    TIME,
    origin_close_time   TIME,
    travel_mode         TEXT,

    CONSTRAINT item_type_valid
        CHECK (item_type IN ('ACTIVITY', 'RESERVATION', 'TRANSPORTATION')),
    CONSTRAINT item_time_range_valid
        CHECK (end_time > start_time),
    CONSTRAINT item_party_size_positive
        CHECK (party_size > 0),
    CONSTRAINT item_travel_mode_valid
        CHECK (travel_mode IN ('WALK', 'DRIVE', 'TRANSIT', 'BICYCLE')),
    CONSTRAINT item_activity_columns
        CHECK (item_type = 'ACTIVITY' OR category IS NULL),
    CONSTRAINT item_reservation_columns
        CHECK (item_type = 'RESERVATION' OR num_nonnulls(confirmation_code, party_size) = 0),
    CONSTRAINT item_transportation_columns
        CHECK (CASE item_type
                   WHEN 'TRANSPORTATION'
                       THEN num_nonnulls(origin_name, origin_latitude, origin_longitude, travel_mode) = 4
                   ELSE num_nonnulls(origin_name, origin_latitude, origin_longitude,
                                     origin_open_time, origin_close_time, travel_mode) = 0
               END)
);

-- items are always looked up by trip; this also speeds up the cascade when a trip is deleted
CREATE INDEX itinerary_item_trip_id_idx ON itinerary_item (trip_id);
