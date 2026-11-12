package io.github.soham023.spottracker.source;

import io.github.soham023.spottracker.domain.PriceSnapshot;

import java.time.LocalDate;
import java.util.List;

/** A source that can return past prices for a date range (used by backfill and the startup ingest). */
public interface HistoricalPriceSource {

    String id();

    boolean isEnabled();

    /** Prices dated within [from, to], both inclusive. */
    List<PriceSnapshot> fetchRange(LocalDate from, LocalDate to) throws Exception;
}
