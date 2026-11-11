package io.github.soham023.spottracker.contract;

import io.github.soham023.spottracker.ingest.SnapshotWriter;
import io.github.soham023.spottracker.source.Fixtures;
import io.github.soham023.spottracker.source.ncdex.NcdexProductPage;
import io.github.soham023.spottracker.source.ncdex.NcdexProductParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real Flyway migrations (V3 contract tables) on H2 in PostgreSQL mode. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:contracts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@Import(ContractDataWriter.class)
class ContractDataWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final LocalDate DEC = LocalDate.of(2026, 12, 18);

    @Autowired
    ContractDataWriter writer;
    @Autowired
    ContractExpiryRepository expiries;
    @Autowired
    DailyMarginRepository margins;
    @Autowired
    PremiumDiscountRepository matrix;

    private final NcdexProductPage page = new NcdexProductParser()
            .parse(Fixtures.read("ncdex-product-turmeric.html"), "TMCFGRNZM");

    @Test
    void rescrapingTheSameDayIsIdempotent() {
        Instant first = Instant.parse("2026-11-11T04:00:00Z");
        Instant second = Instant.parse("2026-11-11T13:00:00Z");

        var c1 = writer.upsert(page, TODAY, first);
        var c2 = writer.upsert(withDecemberTotalMargin(page, "27.79"), TODAY, second);

        assertThat(c1).isEqualTo(new SnapshotWriter.Counts(3 + 3 + 16, 0));
        assertThat(c2).isEqualTo(new SnapshotWriter.Counts(0, 3 + 16)); // expiries already seen today are untouched
        assertThat(expiries.count()).isEqualTo(3);
        assertThat(margins.count()).isEqualTo(3);
        assertThat(matrix.count()).isEqualTo(16);
        DailyMargin dec = margins.findBySymbolAndMarginDateAndFileTypeAndExpiryDate("TMCFGRNZM", TODAY, "Morning", DEC)
                .orElseThrow();
        assertThat(dec.getTotalMargin()).isEqualByComparingTo("27.79");
        assertThat(dec.getFetchedAt()).isEqualTo(second);
        assertThat(matrix.findLatestAsOf("TMCFGRNZM")).contains(TODAY);
    }

    @Test
    void calendarKeepsExpiriesAndTracksWhenTheyWereLastListed() {
        writer.upsert(page, TODAY, Instant.parse("2026-11-11T04:00:00Z"));
        var later = new NcdexProductPage("TMCFGRNZM", null, null, List.of(DEC), List.of(), List.of());
        var c = writer.upsert(later, TODAY.plusDays(1), Instant.parse("2026-11-12T04:00:00Z"));

        assertThat(c).isEqualTo(new SnapshotWriter.Counts(0, 1));
        assertThat(expiries.findBySymbolOrderByExpiryDateAsc("TMCFGRNZM"))
                .extracting(ContractExpiry::getExpiryDate, ContractExpiry::getLastSeen)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DEC, TODAY.plusDays(1)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2027, 4, 16), TODAY),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2027, 5, 20), TODAY));
    }

    @Test
    void databaseRejectsADuplicateMarginKey() {
        Instant at = Instant.parse("2026-11-11T04:00:00Z");
        margins.saveAndFlush(DailyMargin.from("TMCFGRNZM", page.margins().getFirst(), at));
        assertThatThrownBy(() -> margins.saveAndFlush(DailyMargin.from("TMCFGRNZM", page.margins().getFirst(), at)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    static NcdexProductPage withDecemberTotalMargin(NcdexProductPage p, String tm) {
        List<NcdexProductPage.Margin> ms = new ArrayList<>();
        for (NcdexProductPage.Margin m : p.margins()) {
            ms.add(m.expiry().equals(DEC) ? new NcdexProductPage.Margin(m.fileType(), m.marginDate(), m.expiry(),
                    m.initialMargin(), m.extremeMargin(), m.additionalPem(), new BigDecimal(tm), m.additionalLong(),
                    m.additionalShort(), m.specialLong(), m.specialShort()) : m);
        }
        return new NcdexProductPage(p.symbol(), p.lastTradedPrice(), p.change(), p.expiries(), ms, p.premiumDiscounts());
    }
}
