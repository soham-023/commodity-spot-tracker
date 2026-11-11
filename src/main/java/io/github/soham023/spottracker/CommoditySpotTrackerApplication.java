package io.github.soham023.spottracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CommoditySpotTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommoditySpotTrackerApplication.class, args);
    }
}
