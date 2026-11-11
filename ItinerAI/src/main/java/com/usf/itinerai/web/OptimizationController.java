package com.usf.itinerai.web;

import com.usf.itinerai.agent.OptimizationService;
import com.usf.itinerai.web.dto.ApplyRequest;
import com.usf.itinerai.web.dto.OptimizationResponse;
import com.usf.itinerai.web.dto.OptimizeRequest;
import com.usf.itinerai.web.dto.TripResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/optimize")
public class OptimizationController {

    private final OptimizationService optimizationService;

    public OptimizationController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    // asks the agent for a proposal; refining is the same call plus the previous changes and the feedback.
    // Nothing is saved
    @PostMapping
    public OptimizationResponse optimize(@PathVariable long tripId,
                                         @Valid @RequestBody(required = false) OptimizeRequest request) {
        OptimizeRequest body = request == null ? OptimizeRequest.EMPTY : request;
        return OptimizationResponse.from(optimizationService.optimize(tripId, body.toCommand()));
    }

    // writes a proposal's changes to the trip, all or nothing
    @PostMapping("/apply")
    public TripResponse apply(@PathVariable long tripId, @Valid @RequestBody ApplyRequest request) {
        return TripResponse.from(optimizationService.apply(tripId, request.toChanges()));
    }
}
