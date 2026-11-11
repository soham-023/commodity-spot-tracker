package io.github.soham023.spottracker.ingest;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.PriceSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T13:00:00Z"), ZoneOffset.UTC);

    @Test
    void failingSourceDoesNotStopTheOthers() {
        PriceSource broken = source("NCDEX", true, null);
        PriceSource healthy = source("AGMARKNET", true, List.of(PriceSnapshot.spot("TURMERIC", "AGMARKNET", "Sangli",
                "Rajapuri", BigDecimal.TEN, LocalDate.of(2026, 11, 11), clock.instant())));
        PriceSource off = source("OFF", false, List.of());
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.upsert(anyCollection())).thenReturn(new SnapshotWriter.Counts(1, 0));
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

        IngestionRun run = new IngestionService(List.of(), List.of(broken, healthy, off), writer, events, clock).runAll();

        assertThat(run.sources()).extracting(SourceRunResult::source, SourceRunResult::status).containsExactly(
                org.assertj.core.groups.Tuple.tuple("NCDEX", SourceRunResult.Status.FAILED),
                org.assertj.core.groups.Tuple.tuple("AGMARKNET", SourceRunResult.Status.OK),
                org.assertj.core.groups.Tuple.tuple("OFF", SourceRunResult.Status.SKIPPED));
        assertThat(run.sources().get(0).error()).contains("HTTP 503");
        assertThat(run.sources().get(1).inserted()).isEqualTo(1);
        verify(events).publishEvent(any(IngestionCompletedEvent.class));
    }

    @Test
    void referenceJobsRunFirstAndTheirFailureIsIsolated() {
        ReferenceDataJob product = new ReferenceDataJob() {
            @Override
            public String id() {
                return "NCDEX_PRODUCT";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public SourceRunResult run() throws Exception {
                throw new IOException("GET https://www.ncdex.com/products/TMCFGRNZM returned HTTP 502");
            }
        };
        PriceSource healthy = source("AGMARKNET", true, List.of());
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.upsert(anyCollection())).thenReturn(new SnapshotWriter.Counts(0, 0));

        IngestionRun run = new IngestionService(List.of(product), List.of(healthy), writer,
                mock(ApplicationEventPublisher.class), clock).runAll();

        assertThat(run.sources()).extracting(SourceRunResult::source).containsExactly("NCDEX_PRODUCT", "AGMARKNET");
        assertThat(run.sources().get(0).status()).isEqualTo(SourceRunResult.Status.FAILED);
        assertThat(run.sources().get(0).error()).contains("HTTP 502");
        assertThat(run.anySucceeded()).isTrue();
    }

    private static PriceSource source(String id, boolean enabled, List<PriceSnapshot> result) {
        return new PriceSource() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public List<PriceSnapshot> fetch() throws Exception {
                if (result == null) {
                    throw new IOException("GET https://www.ncdex.com returned HTTP 503");
                }
                return result;
            }
        };
    }
}
