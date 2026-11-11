package io.github.soham023.spottracker.ingest;

/**
 * Non-price data refreshed with every ingestion run (contract calendar, margins, delivery premiums).
 * Runs before the price sources so spread analysis sees the current calendar. Implementations fetch
 * and store in one step and throw on failure; like price sources, a failure never stops the run.
 */
public interface ReferenceDataJob {

    String id();

    boolean isEnabled();

    /** Fetches and stores; returns how many rows were fetched, inserted and updated. */
    SourceRunResult run() throws Exception;
}
