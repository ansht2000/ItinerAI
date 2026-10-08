package com.usf.itinerai.web;

import com.usf.itinerai.feasibility.FeasibilityService;
import com.usf.itinerai.trip.Trip;
import com.usf.itinerai.trip.TripService;
import com.usf.itinerai.web.dto.FeasibilityResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/feasibility")
public class FeasibilityController {

    private final TripService tripService;
    private final FeasibilityService feasibilityService;

    public FeasibilityController(TripService tripService, FeasibilityService feasibilityService) {
        this.tripService = tripService;
        this.feasibilityService = feasibilityService;
    }

    // checks the trip's current schedule against every constraint; the result isn't saved yet
    @PostMapping
    public FeasibilityResponse checkTrip(@PathVariable long tripId) {
        Trip trip = tripService.getTrip(tripId);
        return FeasibilityResponse.from(tripId, feasibilityService.check(trip));
    }
}
