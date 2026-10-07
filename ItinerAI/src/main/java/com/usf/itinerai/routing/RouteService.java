package com.usf.itinerai.routing;

import com.usf.itinerai.itinerary.TravelMode;
import com.usf.itinerai.location.Location;

import java.time.Duration;
import java.util.Optional;

// travel time between two places, backed by an external routing API
// a single method, so tests can stub it with a lambda: (origin, destination, mode) -> Optional.of(Duration.ofMinutes(40))
@FunctionalInterface
public interface RouteService {

    // empty when the API has no route for this mode, e.g. no transit between the two places;
    // throws RoutingException when the API can't be reached or rejects the request
    Optional<Duration> travelTime(Location origin, Location destination, TravelMode mode);
}
