package com.usf.itinerai.web.dto;

import com.usf.itinerai.feasibility.Conflict;

import java.util.List;

// the result of checking a trip's schedule; feasible means no constraint found a problem
public record FeasibilityResponse(
        long tripId,
        boolean feasible,
        List<ConflictResponse> conflicts) {

    public static FeasibilityResponse from(long tripId, List<Conflict> conflicts) {
        return new FeasibilityResponse(tripId, conflicts.isEmpty(),
                conflicts.stream().map(ConflictResponse::from).toList());
    }
}
