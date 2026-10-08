package com.usf.itinerai.feasibility;

import com.usf.itinerai.trip.Trip;

import java.util.List;

// one rule a trip's schedule must follow
// a single method, so tests can stub it with a lambda: trip -> List.of()
@FunctionalInterface
public interface Constraint {

    // empty when the trip follows the rule
    List<Conflict> check(Trip trip);
}
