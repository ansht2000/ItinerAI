package com.usf.itinerai.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// RouteService backed by the Google Routes API (computeRoutes)
@Service
public class GoogleRoutesService implements RouteService {

    private static final String BASE_URL = "https://routes.googleapis.com";
    // only ask for the duration: smaller responses, and it keeps requests on the cheapest billing tier
    private static final String FIELD_MASK = "routes.duration";

    private final RestClient restClient;
    private final String apiKey;
    private final Duration cacheTtl;
    private final Clock clock;
    // answers from the last cacheTtl, so re-checking a schedule doesn't ask Google again about legs that didn't
    // change; failed calls are never stored
    private final Map<RouteKey, CachedTravelTime> cache = new ConcurrentHashMap<>();

    @Autowired
    public GoogleRoutesService(RestClient.Builder restClientBuilder, RoutingProperties properties) {
        this(restClientBuilder, properties, Clock.systemUTC());
    }

    // the clock lets tests move time forward to expire cached answers
    GoogleRoutesService(RestClient.Builder restClientBuilder, RoutingProperties properties, Clock clock) {
        if (properties.cacheTtl() == null) {
            throw new IllegalArgumentException("cacheTtl must not be null");
        }
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = properties.googleApiKey();
        this.cacheTtl = properties.cacheTtl();
        this.clock = clock;
    }

    @Override
    public Optional<Duration> travelTime(Location origin, Location destination, TravelMode mode) {
        if (origin == null || destination == null || mode == null) {
            throw new IllegalArgumentException("origin, destination and mode must not be null");
        }
        // same coordinates means nothing to travel, so skip the billed API call
        if (origin.getLatitude() == destination.getLatitude()
                && origin.getLongitude() == destination.getLongitude()) {
            return Optional.of(Duration.ZERO);
        }

        RouteKey key = RouteKey.of(origin, destination, mode);
        Instant now = clock.instant();
        CachedTravelTime cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return Optional.ofNullable(cached.travelTime());
        }

        // a RoutingException leaves the cache untouched, so the next call tries Google again
        Optional<Duration> travelTime = fetchTravelTime(origin, destination, mode);
        if (cacheTtl.isPositive()) {
            // drop expired answers first, so the cache only holds routes looked up recently
            cache.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
            cache.put(key, new CachedTravelTime(travelTime.orElse(null), now.plus(cacheTtl)));
        }
        return travelTime;
    }

    private Optional<Duration> fetchTravelTime(Location origin, Location destination, TravelMode mode) {
        // no departureTime: Google assumes leaving now, which only changes the answer for transit
        ComputeRoutesRequest request =
                new ComputeRoutesRequest(Waypoint.at(origin), Waypoint.at(destination), googleMode(mode));
        ComputeRoutesResponse response;
        try {
            response = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ComputeRoutesResponse.class);
        } catch (RestClientResponseException e) {
            throw new RoutingException(
                    "Google Routes API returned " + e.getStatusCode().value() + ": " + errorMessage(e), e);
        } catch (RestClientException e) {
            throw new RoutingException("could not reach the Google Routes API", e);
        }

        // Google answers {} when it has no route for this mode
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseDuration(response.routes().getFirst().duration()));
    }

    private static String googleMode(TravelMode mode) {
        return switch (mode) {
            case WALK -> "WALK";
            case DRIVE -> "DRIVE";
            case TRANSIT -> "TRANSIT";
            case BICYCLE -> "BICYCLE";
        };
    }

    // Google writes durations as seconds with an "s" suffix, e.g. "2400s" or "90.5s"
    private static Duration parseDuration(String value) {
        if (value == null || !value.endsWith("s")) {
            throw new RoutingException("unexpected duration from Google Routes API: " + value);
        }
        try {
            return Duration.parse("PT" + value.substring(0, value.length() - 1) + "S");
        } catch (DateTimeParseException e) {
            throw new RoutingException("unexpected duration from Google Routes API: " + value, e);
        }
    }

    // Google's own explanation (e.g. "API key not valid") when the error body has one
    private static String errorMessage(RestClientResponseException e) {
        try {
            GoogleErrorResponse body = e.getResponseBodyAs(GoogleErrorResponse.class);
            if (body != null && body.error() != null && body.error().message() != null) {
                return body.error().message();
            }
        } catch (RuntimeException ignored) {
            // not Google's JSON error format; fall back to the status text
        }
        return e.getStatusText();
    }

    // Location has no equals(), so the cache is keyed by the coordinates themselves
    private record RouteKey(double originLatitude, double originLongitude, double destinationLatitude,
                            double destinationLongitude, TravelMode mode) {
        static RouteKey of(Location origin, Location destination, TravelMode mode) {
            return new RouteKey(origin.getLatitude(), origin.getLongitude(), destination.getLatitude(),
                    destination.getLongitude(), mode);
        }
    }

    // travelTime is null when Google had no route for the mode
    private record CachedTravelTime(Duration travelTime, Instant expiresAt) {
    }

    // JSON shapes for computeRoutes: https://developers.google.com/maps/documentation/routes/compute_route_directions
    private record ComputeRoutesRequest(Waypoint origin, Waypoint destination, String travelMode) {
    }

    private record Waypoint(WaypointLocation location) {
        static Waypoint at(Location place) {
            return new Waypoint(new WaypointLocation(new LatLng(place.getLatitude(), place.getLongitude())));
        }
    }

    private record WaypointLocation(LatLng latLng) {
    }

    private record LatLng(double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ComputeRoutesResponse(List<Route> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Route(String duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleErrorResponse(GoogleError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleError(String message) {
    }
}
