package io.github.soham023.spottracker.source;

import io.github.soham023.spottracker.domain.PriceSnapshot;

import java.util.List;

/**
 * A place prices come from (an exchange page, a government API, a fixture file).
 * Implementations should throw on transport or parse failure; the ingestion service
 * isolates failures so one broken source never stops the others.
 */
public interface PriceSource {

    /** Stable identifier stored in {@link PriceSnapshot#getSource()}, e.g. {@code NCDEX}. */
    String id();

    /** False when the source is switched off or lacks the configuration it needs (e.g. an API key). */
    boolean isEnabled();

    /** Fetches the latest available prices. */
    List<PriceSnapshot> fetch() throws Exception;
}
