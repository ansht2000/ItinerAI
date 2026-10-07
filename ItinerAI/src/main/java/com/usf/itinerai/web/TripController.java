package com.usf.itinerai.web;

import com.usf.itinerai.trip.Trip;
import com.usf.itinerai.trip.TripService;
import com.usf.itinerai.web.dto.TripRequest;
import com.usf.itinerai.web.dto.TripResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(@Valid @RequestBody TripRequest request) {
        Trip trip = tripService.createTrip(request.toTrip(null));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{tripId}")
                .buildAndExpand(trip.getId())
                .toUri();
        return ResponseEntity.created(location).body(TripResponse.from(trip));
    }

    @GetMapping
    public List<TripResponse> getTrips() {
        return tripService.getTrips().stream().map(TripResponse::from).toList();
    }

    @GetMapping("/{tripId}")
    public TripResponse getTrip(@PathVariable long tripId) {
        return TripResponse.from(tripService.getTrip(tripId));
    }

    // replaces the trip's own fields; its items stay as they are
    @PutMapping("/{tripId}")
    public TripResponse updateTrip(@PathVariable long tripId, @Valid @RequestBody TripRequest request) {
        return TripResponse.from(tripService.updateTrip(request.toTrip(tripId)));
    }

    // also deletes the trip's items
    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@PathVariable long tripId) {
        tripService.deleteTrip(tripId);
    }
}
