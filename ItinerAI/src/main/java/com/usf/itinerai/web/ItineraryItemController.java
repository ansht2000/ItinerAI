package com.usf.itinerai.web;

import com.usf.itinerai.itinerary.ItineraryItem;
import com.usf.itinerai.trip.TripService;
import com.usf.itinerai.web.dto.ItemRequest;
import com.usf.itinerai.web.dto.ItemResponse;
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
@RequestMapping("/api/trips/{tripId}/items")
public class ItineraryItemController {

    private final TripService tripService;

    public ItineraryItemController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<ItemResponse> addItem(@PathVariable long tripId, @Valid @RequestBody ItemRequest request) {
        ItineraryItem item = tripService.addItem(tripId, request.toItem(null));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{itemId}")
                .buildAndExpand(item.getId())
                .toUri();
        return ResponseEntity.created(location).body(ItemResponse.from(item));
    }

    // sorted by start time
    @GetMapping
    public List<ItemResponse> getItems(@PathVariable long tripId) {
        return tripService.getItems(tripId).stream().map(ItemResponse::from).toList();
    }

    @GetMapping("/{itemId}")
    public ItemResponse getItem(@PathVariable long tripId, @PathVariable long itemId) {
        return ItemResponse.from(tripService.getItem(tripId, itemId));
    }

    // replaces the whole item; the body's type may differ from the stored one
    @PutMapping("/{itemId}")
    public ItemResponse updateItem(@PathVariable long tripId, @PathVariable long itemId,
                                   @Valid @RequestBody ItemRequest request) {
        return ItemResponse.from(tripService.updateItem(tripId, request.toItem(itemId)));
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable long tripId, @PathVariable long itemId) {
        tripService.deleteItem(tripId, itemId);
    }
}
