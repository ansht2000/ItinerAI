package com.usf.itinerai.agent;

// a proposal's change no longer matches the saved trip, because the trip was edited after the proposal was made
public class StaleProposalException extends RuntimeException {

    public StaleProposalException(String message) {
        super(message);
    }
}
