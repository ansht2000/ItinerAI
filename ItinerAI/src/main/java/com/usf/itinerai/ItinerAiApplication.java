package com.usf.itinerai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// remove "(exclude = {DataSourceAutoConfiguration.class})" after database is added and running
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@RestController
public class ItinerAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItinerAiApplication.class, args);
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Hello %s!", name);
    }
}
