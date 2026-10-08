package com.usf.itinerai.web;

import com.jayway.jsonpath.JsonPath;
import com.usf.itinerai.TestcontainersConfiguration;
import com.usf.itinerai.routing.RouteService;
import com.usf.itinerai.routing.RoutingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// goes through the real HTTP layer, constraints and Postgres in Docker, but with a stand-in for Google
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FeasibilityControllerTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String LOUVRE = """
            {"name": "Louvre", "latitude": 48.8606, "longitude": 2.3376}""";
    private static final String BISTRO = """
            {"name": "Bistro", "latitude": 48.8530, "longitude": 2.3499}""";

    @Autowired
    private MockMvc mvc;

    // replaces GoogleRoutesService, so no API key or network is needed
    @MockitoBean
    private RouteService routeService;

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
    void tripWithEnoughTimeIsFeasible() throws Exception {
        travelTakes(Duration.ofMinutes(10));
        addActivity("Museum", LOUVRE, "10:00", "12:00");
        addActivity("Lunch", BISTRO, "12:30", "13:30");

        mvc.perform(post(feasibility()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.conflicts").isEmpty());
    }

    // the spec's first use case: 20 minutes between two places that are 40 minutes apart, plus an overlap
    @Test
    void reportsEachConflictWithTheItemsInvolved() throws Exception {
        travelTakes(Duration.ofMinutes(40));
        long museumId = addActivity("Museum", LOUVRE, "10:00", "12:00");
        long lunchId = addActivity("Lunch", BISTRO, "12:20", "13:20");
        long tourId = addActivity("Wine tasting", BISTRO, "13:00", "14:00");

        mvc.perform(post(feasibility()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.conflicts.length()").value(2))
                .andExpect(jsonPath("$.conflicts[0].type").value("OVERLAP"))
                .andExpect(jsonPath("$.conflicts[0].itemIds[0]").value(lunchId))
                .andExpect(jsonPath("$.conflicts[0].itemIds[1]").value(tourId))
                .andExpect(jsonPath("$.conflicts[1].type").value("INSUFFICIENT_TRAVEL_TIME"))
                .andExpect(jsonPath("$.conflicts[1].itemIds[0]").value(museumId))
                .andExpect(jsonPath("$.conflicts[1].itemIds[1]").value(lunchId))
                .andExpect(jsonPath("$.conflicts[1].reason").value(containsString("40 min needed, 20 min available")));
    }

    @Test
    void unknownTripIsNotFound() throws Exception {
        mvc.perform(post("/api/trips/{tripId}/feasibility", -1))
                .andExpect(status().isNotFound());
    }

    @Test
    void routingFailureIsReportedAsBadGateway() throws Exception {
        when(routeService.travelTime(any(), any(), any())).thenThrow(new RoutingException(
                "Google Routes API returned 400: API key not valid. Please pass a valid API key."));
        addActivity("Museum", LOUVRE, "10:00", "12:00");
        addActivity("Lunch", BISTRO, "12:30", "13:30");

        mvc.perform(post(feasibility()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(containsString("API key not valid")));
    }

    private void travelTakes(Duration duration) {
        when(routeService.travelTime(any(), any(), any())).thenReturn(Optional.of(duration));
    }

    private long addActivity(String title, String location, String start, String end) throws Exception {
        String body = mvc.perform(post("/api/trips/{tripId}/items", tripId).contentType(JSON).content("""
                        {"type": "activity", "title": "%s", "startTime": "%s", "endTime": "%s", "location": %s}
                        """.formatted(title, start, end, location)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private String feasibility() {
        return "/api/trips/" + tripId + "/feasibility";
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
