package io.github.soham023.spottracker.api;

import io.github.soham023.spottracker.ingest.BackfillService;
import io.github.soham023.spottracker.ingest.IngestionRun;
import io.github.soham023.spottracker.ingest.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/ingest")
@Tag(name = "Ingestion")
public class IngestController {

    private static final int DEFAULT_BACKFILL_DAYS = 30;

    private final IngestionService ingestion;
    private final BackfillService backfill;
    private final Clock clock;

    public IngestController(IngestionService ingestion, BackfillService backfill, Clock clock) {
        this.ingestion = ingestion;
        this.backfill = backfill;
        this.clock = clock;
    }

    @PostMapping("/run")
    @Operation(summary = "Run every enabled price source now", description =
            "Same work as the daily scheduled job. Each source reports OK, FAILED or SKIPPED independently.")
    public IngestionRun run() {
        return ingestion.runAll();
    }

    @PostMapping("/backfill")
    @Operation(summary = "Backfill NCDEX futures and spot history for a date range", description =
            "Futures: daily OHLC for every TMCFGRNZM expiry trading in the range (NCDEX Futures Prices). Spot: polled "
                    + "spot for Farmer Polished, Unpolished and Rajapore (NCDEX only serves ~30 days back). Upserts, "
                    + "so re-running is safe. Defaults: to = today, from = to - 30 days.")
    public IngestionRun backfill(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange range = DateRange.of(from, to, LocalDate.now(clock), DEFAULT_BACKFILL_DAYS);
        return backfill.backfill(range.from(), range.to());
    }
}
