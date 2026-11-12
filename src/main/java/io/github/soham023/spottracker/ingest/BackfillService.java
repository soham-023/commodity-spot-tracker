package io.github.soham023.spottracker.ingest;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.HistoricalPriceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** Pulls a date range from every {@link HistoricalPriceSource} and upserts it; failures are isolated per source. */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    private final List<HistoricalPriceSource> sources;
    private final SnapshotWriter writer;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public BackfillService(List<HistoricalPriceSource> sources, SnapshotWriter writer, Clock clock) {
        this.sources = sources;
        this.writer = writer;
        this.clock = clock;
    }

    public IngestionRun backfill(LocalDate from, LocalDate to) {
        lock.lock();
        try {
            Instant started = clock.instant();
            List<SourceRunResult> results = new ArrayList<>();
            for (HistoricalPriceSource source : sources) {
                if (!source.isEnabled()) {
                    results.add(SourceRunResult.skipped(source.id()));
                    continue;
                }
                try {
                    List<PriceSnapshot> rows = source.fetchRange(from, to);
                    SnapshotWriter.Counts c = writer.upsert(rows);
                    log.info("Backfill {} {}..{}: {} rows ({} new, {} updated)", source.id(), from, to, rows.size(),
                            c.inserted(), c.updated());
                    results.add(new SourceRunResult(source.id(), SourceRunResult.Status.OK, rows.size(), c.inserted(),
                            c.updated(), null));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(SourceRunResult.failed(source.id(), e));
                } catch (Exception e) {
                    log.warn("Backfill {} failed: {}", source.id(), e.getMessage(), e);
                    results.add(SourceRunResult.failed(source.id(), e));
                }
            }
            return new IngestionRun(started, clock.instant(), List.copyOf(results));
        } finally {
            lock.unlock();
        }
    }
}
