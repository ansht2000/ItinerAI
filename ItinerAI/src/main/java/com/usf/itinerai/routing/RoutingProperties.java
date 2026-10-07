package com.usf.itinerai.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

// settings under itinerai.routing in application.properties
@ConfigurationProperties("itinerai.routing")
public record RoutingProperties(String googleApiKey) {
}
