package com.usf.itinerai.feasibility;

import com.usf.itinerai.trip.Trip;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

// runs every Constraint bean against a trip, so a new constraint (e.g. operating hours) needs no change here
@Service
public class FeasibilityService {

    private final List<Constraint> constraints;

    public FeasibilityService(List<Constraint> constraints) {
        this.constraints = List.copyOf(constraints);
    }

    // every conflict any constraint found, grouped by type in ConflictType's order; empty when the trip is feasible.
    // a RoutingException is passed on, since travel times couldn't be checked
    public List<Conflict> check(Trip trip) {
        return constraints.stream()
                .flatMap(constraint -> constraint.check(trip).stream())
                .sorted(Comparator.comparing(Conflict::type))
                .toList();
    }
}
