package com.usf.itinerai.web.dto;

import com.usf.itinerai.agent.Optimization;

import java.util.List;

// a proposal from the agent; feasible means the proposed day has no conflicts, finished is false when the agent
// stopped at a limit, and items is the whole proposed day
public record OptimizationResponse(
        long tripId,
        boolean feasible,
        boolean finished,
        String summary,
        List<ChangeDto> changes,
        List<ConflictResponse> conflictsBefore,
        List<ConflictResponse> conflictsAfter,
        List<ItemResponse> items) {

    public static OptimizationResponse from(Optimization optimization) {
        return new OptimizationResponse(
                optimization.tripId(),
                optimization.feasible(),
                optimization.finished(),
                optimization.summary(),
                optimization.changes().stream().map(ChangeDto::from).toList(),
                optimization.conflictsBefore().stream().map(ConflictResponse::from).toList(),
                optimization.conflictsAfter().stream().map(ConflictResponse::from).toList(),
                optimization.items().stream().map(ItemResponse::from).toList());
    }
}
