package io.github.soham023.spottracker.ingest;

/** Outcome of running one {@link io.github.soham023.spottracker.source.PriceSource}. */
public record SourceRunResult(String source, Status status, int fetched, int inserted, int updated, String error) {

    public enum Status { OK, FAILED, SKIPPED }

    static SourceRunResult skipped(String source) {
        return new SourceRunResult(source, Status.SKIPPED, 0, 0, 0, "disabled or not configured");
    }

    static SourceRunResult failed(String source, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new SourceRunResult(source, Status.FAILED, 0, 0, 0, msg);
    }
}
