package com.usf.itinerai.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// settings under itinerai.routing in application.properties
@ConfigurationProperties("itinerai.routing")
public record RoutingProperties(
        String googleApiKey,
        // how long a travel time from Google is reused; zero turns the cache off
        @DefaultValue("1h") Duration cacheTtl) {
}
