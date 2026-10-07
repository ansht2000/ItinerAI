package com.usf.itinerai.web;

import com.jayway.jsonpath.JsonPath;
import com.usf.itinerai.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// goes through the real HTTP layer, service and Postgres in Docker; each test's rows are rolled back afterwards
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ItineraryItemControllerTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final long MISSING_ID = -1;
    private static final String LOUVRE = """
            {"name": "Louvre", "latitude": 48.8606, "longitude": 2.3376, "openTime": "09:00", "closeTime": "18:00"}""";
    private static final String HOTEL = """
            {"name": "Hotel", "latitude": 48.8566, "longitude": 2.3522}""";
    private static final String BISTRO = """
            {"name": "Bistro", "latitude": 48.8530, "longitude": 2.3499}""";
    private static final String MUSEUM = """
            {"type": "activity", "title": "Museum", "startTime": "10:00", "endTime": "12:00",
             "location": %s, "category": "art"}
            """.formatted(LOUVRE);
    private static final String METRO = """
            {"type": "transportation", "title": "Metro", "startTime": "09:00", "endTime": "09:30",
             "origin": %s, "destination": %s, "mode": "TRANSIT"}
            """.formatted(HOTEL, LOUVRE);

    @Autowired
    private MockMvc mvc;

    private long tripId;

    @BeforeEach
    void createTrip() throws Exception {
        String body = mvc.perform(post("/api/trips").contentType(JSON).content("""
                        {"name": "Paris day", "destination": "Paris", "date": "2026-11-01",
                         "timeZone": "Europe/Paris", "defaultTravelMode": "WALK"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        tripId = idOf(body);
    }

    @Test
    void addsEachItemType() throws Exception {
        mvc.perform(post(items()).contentType(JSON).content(MUSEUM))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/trips/" + tripId + "/items/\\d+")))
                .andExpect(jsonPath("$.type").value("activity"))
                .andExpect(jsonPath("$.category").value("art"))
                .andExpect(jsonPath("$.location.name").value("Louvre"))
                .andExpect(jsonPath("$.location.openTime").value(startsWith("09:00")));
        mvc.perform(post(items()).contentType(JSON).content("""
                        {"type": "reservation", "title": "Lunch", "startTime": "12:30", "endTime": "13:30",
                         "location": %s, "confirmationCode": "ABC123", "partySize": 2}
                        """.formatted(BISTRO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("reservation"))
                .andExpect(jsonPath("$.confirmationCode").value("ABC123"))
                .andExpect(jsonPath("$.partySize").value(2));
        mvc.perform(post(items()).contentType(JSON).content(METRO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("transportation"))
                .andExpect(jsonPath("$.origin.name").value("Hotel"))
                .andExpect(jsonPath("$.destination.name").value("Louvre"))
                .andExpect(jsonPath("$.mode").value("TRANSIT"))
                .andExpect(jsonPath("$.location").doesNotExist());

        mvc.perform(get("/api/trips/{tripId}", tripId))
                .andExpect(jsonPath("$.items[*].title", contains("Metro", "Museum", "Lunch")));
    }

    @Test
    void getsOneItemOrAllOfThem() throws Exception {
        long museumId = addItem(MUSEUM);
        addItem(METRO);

        mvc.perform(get(item(museumId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(museumId))
                .andExpect(jsonPath("$.title").value("Museum"));
        mvc.perform(get(items()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Metro", "Museum")));
    }

    @Test
    void updateCanChangeTheItemsType() throws Exception {
        long itemId = addItem(MUSEUM);

        mvc.perform(put(item(itemId)).contentType(JSON).content(METRO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.type").value("transportation"));
        mvc.perform(get(item(itemId)))
                .andExpect(jsonPath("$.mode").value("TRANSIT"));
    }

    @Test
    void deletedItemIsGone() throws Exception {
        long itemId = addItem(MUSEUM);

        mvc.perform(delete(item(itemId)))
                .andExpect(status().isNoContent());

        mvc.perform(get(item(itemId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("item " + itemId + " not found in trip " + tripId));
    }

    @Test
    void unknownTripOrItemIsNotFound() throws Exception {
        mvc.perform(post("/api/trips/{tripId}/items", MISSING_ID).contentType(JSON).content(MUSEUM))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/{tripId}/items", MISSING_ID))
                .andExpect(status().isNotFound());
        mvc.perform(get(item(MISSING_ID)))
                .andExpect(status().isNotFound());
        mvc.perform(put(item(MISSING_ID)).contentType(JSON).content(MUSEUM))
                .andExpect(status().isNotFound());
        mvc.perform(delete(item(MISSING_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidItemIsRejectedWithItsFieldErrors() throws Exception {
        mvc.perform(post(items()).contentType(JSON).content("""
                        {"type": "activity", "title": "Museum", "startTime": "12:00", "endTime": "10:00",
                         "location": {"name": "Louvre", "latitude": 48.8606}}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.timeRangeValid").exists())
                .andExpect(jsonPath("$.errors['location.longitude']").exists());
    }

    @Test
    void unknownItemTypeIsRejected() throws Exception {
        mvc.perform(post(items()).contentType(JSON).content(MUSEUM.replace("\"activity\"", "\"tour\"")))
                .andExpect(status().isBadRequest());
    }

    private String items() {
        return "/api/trips/" + tripId + "/items";
    }

    private String item(long itemId) {
        return items() + "/" + itemId;
    }

    private long addItem(String json) throws Exception {
        String body = mvc.perform(post(items()).contentType(JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
