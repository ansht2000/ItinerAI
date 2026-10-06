package com.usf.itinerai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ItinerAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItinerAiApplication.class, args);
    }
}
