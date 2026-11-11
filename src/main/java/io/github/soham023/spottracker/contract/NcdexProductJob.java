package io.github.soham023.spottracker.contract;

import io.github.soham023.spottracker.ingest.ReferenceDataJob;
import io.github.soham023.spottracker.ingest.SourceRunResult;
import io.github.soham023.spottracker.source.ncdex.NcdexProductPage;
import io.github.soham023.spottracker.source.ncdex.NcdexProductSource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Scrapes the NCDEX product page and stores its calendar, margins and premium/discount matrix. */
@Component
public class NcdexProductJob implements ReferenceDataJob {

    private final NcdexProductSource source;
    private final ContractDataWriter writer;
    private final Clock clock;

    public NcdexProductJob(NcdexProductSource source, ContractDataWriter writer, Clock clock) {
        this.source = source;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public String id() {
        return NcdexProductSource.ID;
    }

    @Override
    public boolean isEnabled() {
        return source.isEnabled();
    }

    @Override
    public SourceRunResult run() throws Exception {
        NcdexProductPage page = source.fetch();
        var counts = writer.upsert(page, LocalDate.now(clock), clock.instant());
        int fetched = page.expiries().size() + page.margins().size() + page.premiumDiscounts().size();
        return new SourceRunResult(id(), SourceRunResult.Status.OK, fetched, counts.inserted(), counts.updated(), null);
    }
}
