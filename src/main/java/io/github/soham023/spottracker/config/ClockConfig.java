package io.github.soham023.spottracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** All "today" logic runs on Indian market time. */
    public static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock() {
        return Clock.system(MARKET_ZONE);
    }
}
