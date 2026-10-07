package com.usf.itinerai.routing;

import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// runs against a mock HTTP server, so no API key or network is needed
class GoogleRoutesServiceTest {

    private static final String URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final Location LOUVRE = new Location("Louvre", 48.8606, 2.3376, null, null);
    private static final Location EIFFEL_TOWER = new Location("Eiffel Tower", 48.8584, 2.2945, null, null);

    private MockRestServiceServer server;
    private GoogleRoutesService routes;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        routes = new GoogleRoutesService(builder, new RoutingProperties("test-key"));
    }

    @Test
    void sendsCoordinatesModeAndKeyAndReturnsTheDuration() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", "routes.duration"))
                .andExpect(content().json("""
                        {"origin": {"location": {"latLng": {"latitude": 48.8606, "longitude": 2.3376}}},
                         "destination": {"location": {"latLng": {"latitude": 48.8584, "longitude": 2.2945}}},
                         "travelMode": "TRANSIT"}
                        """))
                .andRespond(withSuccess("""
                        {"routes": [{"duration": "2400s"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(routes.travelTime(LOUVRE, EIFFEL_TOWER, TravelMode.TRANSIT)).contains(Duration.ofMinutes(40));
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(TravelMode.class)
    void sendsEachTravelModeByGooglesName(TravelMode mode) {
        server.expect(jsonPath("$.travelMode").value(mode.name()))
                .andRespond(withSuccess("""
                        {"routes": [{"duration": "60s"}]}
                        """, MediaType.APPLICATION_JSON));

        routes.travelTime(LOUVRE, EIFFEL_TOWER, mode);
        server.verify();
    }

    @Test
    void fractionalSecondsAreKept() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"routes": [{"duration": "90.5s"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(routes.travelTime(LOUVRE, EIFFEL_TOWER, TravelMode.WALK)).contains(Duration.ofMillis(90_500));
    }

    @Test
    void noRouteIsEmpty() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(routes.travelTime(LOUVRE, EIFFEL_TOWER, TravelMode.TRANSIT)).isEmpty();
    }

    @Test
    void samePlaceIsZeroWithoutCallingTheApi() {
        // no request is expected, so any API call fails the test
        assertThat(routes.travelTime(LOUVRE, LOUVRE, TravelMode.WALK)).contains(Duration.ZERO);
        server.verify();
    }

    @Test
    void apiErrorBecomesRoutingExceptionWithGooglesMessage() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"code": 400, "status": "INVALID_ARGUMENT",
                                           "message": "API key not valid. Please pass a valid API key."}}
                                """));

        assertThatThrownBy(() -> routes.travelTime(LOUVRE, EIFFEL_TOWER, TravelMode.WALK))
                .isInstanceOf(RoutingException.class)
                .hasMessage("Google Routes API returned 400: API key not valid. Please pass a valid API key.");
    }

    @Test
    void networkFailureBecomesRoutingException() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> routes.travelTime(LOUVRE, EIFFEL_TOWER, TravelMode.WALK))
                .isInstanceOf(RoutingException.class)
                .hasMessage("could not reach the Google Routes API");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatThrownBy(() -> routes.travelTime(null, EIFFEL_TOWER, TravelMode.WALK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
