package io.github.soham023.spottracker.domain;

import io.github.soham023.spottracker.ingest.SnapshotWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real Flyway migrations on H2 in PostgreSQL mode. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:repo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@Import(SnapshotWriter.class)
class PriceSnapshotRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    @Autowired
    PriceSnapshotRepository repository;

    @Autowired
    SnapshotWriter writer;

    private static List<PriceSnapshot> batch(String futuresPrice, Instant at) {
        return List.of(
                PriceSnapshot.spot("TURMERIC", "NCDEX", "Nizamabad", "Farmer Polished", new BigDecimal("24954.10"),
                        LocalDate.of(2026, 11, 4), at),
                PriceSnapshot.spot("TURMERIC", "NCDEX", "Nizamabad", "Unpolished", new BigDecimal("23698.55"),
                        LocalDate.of(2026, 11, 4), at),
                PriceSnapshot.futures("TURMERIC", "NCDEX", "Nizamabad", "2026-12", new BigDecimal(futuresPrice), DAY, at),
                PriceSnapshot.futures("TURMERIC", "NCDEX", "Nizamabad", "2027-04", new BigDecimal("28210"), DAY, at));
    }

    @Test
    void rerunningIngestionOnTheSameDayIsIdempotent() {
        Instant first = Instant.parse("2026-11-11T13:00:00Z");
        Instant second = Instant.parse("2026-11-11T14:00:00Z");

        var c1 = writer.upsert(batch("28560", first));
        var c2 = writer.upsert(batch("28600", second));

        assertThat(c1).isEqualTo(new SnapshotWriter.Counts(4, 0));
        assertThat(c2).isEqualTo(new SnapshotWriter.Counts(0, 4));
        assertThat(repository.count()).isEqualTo(4);
        PriceSnapshot dec = repository.findBySourceAndMarketAndVarietyAndPriceTypeAndContractMonthAndPriceDate(
                "NCDEX", "Nizamabad", "", PriceType.FUTURES, "2026-12", DAY).orElseThrow();
        assertThat(dec.getPrice()).isEqualByComparingTo("28600");
        assertThat(dec.getFetchedAt()).isEqualTo(second);
    }

    @Test
    void databaseRejectsADuplicateNaturalKey() {
        Instant at = Instant.parse("2026-11-11T13:00:00Z");
        repository.saveAndFlush(PriceSnapshot.futures("TURMERIC", "NCDEX", "Nizamabad", "2026-12", BigDecimal.ONE, DAY, at));

        assertThatThrownBy(() -> repository.saveAndFlush(
                PriceSnapshot.futures("TURMERIC", "NCDEX", "Nizamabad", "2026-12", BigDecimal.TEN, DAY, at)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void specificationsFilterBySourceTypeAndDateCaseInsensitively() {
        writer.upsert(batch("28560", Instant.parse("2026-11-11T13:00:00Z")));

        var futures = repository.findAll(PriceSnapshotSpecs.filter("turmeric", "ncdex", null, PriceType.FUTURES,
                DAY, DAY), Pageable.unpaged());
        var spotsBeforeDay = repository.findAll(PriceSnapshotSpecs.filter("TURMERIC", null, "nizamabad", PriceType.SPOT,
                null, DAY.minusDays(1)), Pageable.unpaged());

        assertThat(futures.getTotalElements()).isEqualTo(2);
        assertThat(spotsBeforeDay.getContent()).extracting(PriceSnapshot::getVariety)
                .containsExactlyInAnyOrder("Farmer Polished", "Unpolished");
        assertThat(repository.findLatestDate("TURMERIC")).contains(DAY);
    }
}
