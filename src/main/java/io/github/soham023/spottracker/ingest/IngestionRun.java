package io.github.soham023.spottracker.ingest;

import java.time.Instant;
import java.util.List;

public record IngestionRun(Instant startedAt, Instant finishedAt, List<SourceRunResult> sources) {

    public boolean anySucceeded() {
        return sources.stream().anyMatch(s -> s.status() == SourceRunResult.Status.OK);
    }
}
