package io.github.soham023.spottracker.ingest;

/** Published after every ingestion run so spread analysis and alerting can react. */
public record IngestionCompletedEvent(IngestionRun run) {
}
