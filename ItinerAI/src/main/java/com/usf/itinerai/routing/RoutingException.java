package com.usf.itinerai.routing;

// the routing API couldn't be reached or rejected the request (bad API key, quota, outage);
// a missing route is not an error: RouteService returns an empty Optional for that
public class RoutingException extends RuntimeException {

    public RoutingException(String message) {
        super(message);
    }

    public RoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
