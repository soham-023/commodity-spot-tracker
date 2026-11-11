package io.github.soham023.spottracker.ingest;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/** Upserts snapshots by natural key, which is what makes re-running an ingestion idempotent. */
@Service
public class SnapshotWriter {

    public record Counts(int inserted, int updated) {
    }

    private final PriceSnapshotRepository repository;

    public SnapshotWriter(PriceSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Counts upsert(Collection<PriceSnapshot> snapshots) {
        int inserted = 0;
        int updated = 0;
        for (PriceSnapshot s : snapshots) {
            var existing = repository.findBySourceAndMarketAndVarietyAndPriceTypeAndContractMonthAndPriceDate(
                    s.getSource(), s.getMarket(), s.getVariety(), s.getPriceType(), s.getContractMonth(), s.getPriceDate());
            if (existing.isPresent()) {
                existing.get().refreshFrom(s);
                updated++;
            } else {
                repository.save(s);
                inserted++;
            }
        }
        return new Counts(inserted, updated);
    }
}
