package io.github.soham023.spottracker.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * After the seed loader: runs the live sources once and backfills the last {@code startup-backfill-days}
 * of NCDEX history, on a background thread so a slow or unreachable exchange never delays startup.
 * {@code SPOT_INGEST_ON_STARTUP=false} turns it off.
 */
@Component
@Profile("!demo")
@Order(20)
@ConditionalOnProperty(prefix = "spot.ingest", name = "on-startup", havingValue = "true", matchIfMissing = true)
public class StartupIngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIngestRunner.class);

    private final IngestionService ingestion;
    private final BackfillService backfill;
    private final Clock clock;
    private final int backfillDays;

    public StartupIngestRunner(IngestionService ingestion, BackfillService backfill, Clock clock,
                               @Value("${spot.ingest.startup-backfill-days:30}") int backfillDays) {
        this.ingestion = ingestion;
        this.backfill = backfill;
        this.clock = clock;
        this.backfillDays = backfillDays;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(this::ingest, "startup-ingest");
        t.setDaemon(true);
        t.start();
    }

    void ingest() {
        try {
            LocalDate today = LocalDate.now(clock);
            if (backfillDays > 0) {
                log.info("Startup backfill: {}", backfill.backfill(today.minusDays(backfillDays), today).sources());
            }
            log.info("Startup ingestion: {}", ingestion.runAll().sources());
        } catch (RuntimeException e) {
            log.warn("Startup ingestion failed: {}", e.getMessage(), e);
        }
    }
}
