package io.github.soham023.spottracker.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Daily ingestion. Default 18:30 IST: after NCDEX agri futures close (17:00) and after most
 * APMC yards have reported the day's arrivals. Override with {@code SPOT_INGEST_CRON}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "spot.ingest", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final IngestionService ingestion;

    public IngestionScheduler(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Scheduled(cron = "${spot.ingest.cron:0 30 18 * * *}", zone = "${spot.ingest.zone:Asia/Kolkata}")
    public void daily() {
        IngestionRun run = ingestion.runAll();
        log.info("Scheduled ingestion finished: {}", run.sources());
    }
}
