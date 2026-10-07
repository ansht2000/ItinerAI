package com.usf.itinerai.web;

import com.jayway.jsonpath.JsonPath;
import com.usf.itinerai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
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
class TripControllerTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final long MISSING_ID = -1;
    private static final String PARIS = """
            {"name": "Paris day", "destination": "Paris", "date": "2026-11-01",
             "timeZone": "Europe/Paris", "defaultTravelMode": "WALK"}
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void createReturnsTheTripAndWhereToFindIt() throws Exception {
        mvc.perform(post("/api/trips").contentType(JSON).content(PARIS))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/trips/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Paris day"))
                .andExpect(jsonPath("$.destination").value("Paris"))
                .andExpect(jsonPath("$.date").value("2026-11-01"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
                .andExpect(jsonPath("$.defaultTravelMode").value("WALK"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void getReturnsTheTrip() throws Exception {
        long tripId = createTrip(PARIS);

        mvc.perform(get("/api/trips/{tripId}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.name").value("Paris day"));
    }

    @Test
    void listIncludesEveryTrip() throws Exception {
        createTrip(PARIS);
        createTrip(PARIS.replace("Paris day", "Second day"));

        mvc.perform(get("/api/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Paris day", "Second day")));
    }

    @Test
    void updateReplacesTheTripsFields() throws Exception {
        long tripId = createTrip(PARIS);

        mvc.perform(put("/api/trips/{tripId}", tripId).contentType(JSON).content("""
                        {"name": "Paris weekend", "destination": "Paris", "date": "2026-11-02",
                         "timeZone": "Europe/Paris", "defaultTravelMode": "TRANSIT"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.name").value("Paris weekend"))
                .andExpect(jsonPath("$.defaultTravelMode").value("TRANSIT"));
        mvc.perform(get("/api/trips/{tripId}", tripId))
                .andExpect(jsonPath("$.date").value("2026-11-02"));
    }

    @Test
    void deletedTripIsGone() throws Exception {
        long tripId = createTrip(PARIS);

        mvc.perform(delete("/api/trips/{tripId}", tripId))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/trips/{tripId}", tripId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("trip " + tripId + " not found"));
    }

    @Test
    void unknownTripIsNotFound() throws Exception {
        mvc.perform(get("/api/trips/{tripId}", MISSING_ID))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/trips/{tripId}", MISSING_ID).contentType(JSON).content(PARIS))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/trips/{tripId}", MISSING_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTripIsRejectedWithItsFieldErrors() throws Exception {
        mvc.perform(post("/api/trips").contentType(JSON).content("""
                        {"name": "", "destination": "Paris", "timeZone": "Europe/Paris", "defaultTravelMode": "WALK"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.date").exists())
                .andExpect(jsonPath("$.errors.destination").doesNotExist());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/trips").contentType(JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTimeZoneIsRejected() throws Exception {
        mvc.perform(post("/api/trips").contentType(JSON).content(PARIS.replace("Europe/Paris", "Mars/Olympus")))
                .andExpect(status().isBadRequest());
    }

    private long createTrip(String json) throws Exception {
        String body = mvc.perform(post("/api/trips").contentType(JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
