package io.github.soham023.spottracker.ingest;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.PriceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs every configured {@link PriceSource}. A failing source is logged and reported but never
 * stops the others; overlapping runs (scheduler + manual trigger) are serialised.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final List<PriceSource> sources;
    private final SnapshotWriter writer;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public IngestionService(List<PriceSource> sources, SnapshotWriter writer, ApplicationEventPublisher events, Clock clock) {
        this.sources = sources;
        this.writer = writer;
        this.events = events;
        this.clock = clock;
    }

    public IngestionRun runAll() {
        lock.lock();
        try {
            Instant started = clock.instant();
            List<SourceRunResult> results = new ArrayList<>();
            for (PriceSource source : sources) {
                results.add(runOne(source));
            }
            IngestionRun run = new IngestionRun(started, clock.instant(), List.copyOf(results));
            events.publishEvent(new IngestionCompletedEvent(run));
            return run;
        } finally {
            lock.unlock();
        }
    }

    private SourceRunResult runOne(PriceSource source) {
        if (!source.isEnabled()) {
            log.info("Source {} skipped (disabled or missing configuration)", source.id());
            return SourceRunResult.skipped(source.id());
        }
        try {
            List<PriceSnapshot> snapshots = source.fetch();
            SnapshotWriter.Counts counts = writer.upsert(snapshots);
            log.info("Source {} fetched {} prices ({} new, {} updated)", source.id(), snapshots.size(),
                    counts.inserted(), counts.updated());
            return new SourceRunResult(source.id(), SourceRunResult.Status.OK, snapshots.size(),
                    counts.inserted(), counts.updated(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SourceRunResult.failed(source.id(), e);
        } catch (Exception e) {
            log.warn("Source {} failed: {}", source.id(), e.getMessage(), e);
            return SourceRunResult.failed(source.id(), e);
        }
    }
}
