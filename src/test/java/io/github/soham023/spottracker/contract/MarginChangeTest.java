package io.github.soham023.spottracker.contract;

import io.github.soham023.spottracker.source.ncdex.NcdexProductPage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarginChangeTest {

    private static final LocalDate DEC = LocalDate.of(2026, 12, 18);
    private static final LocalDate APR = LocalDate.of(2027, 4, 16);
    private static final LocalDate D1 = LocalDate.of(2026, 11, 11);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 12);

    @Test
    void comparesTheTwoLatestMarginDatesOfTheExpiry() {
        List<DailyMargin> rows = List.of(
                margin(D1, DEC, "17.79", "2026-11-11T03:00:00Z"),
                margin(D1, APR, "18.47", "2026-11-11T03:00:00Z"),
                margin(D2, DEC, "22.79", "2026-11-12T03:00:00Z"),
                margin(D2, DEC, "27.79", "2026-11-12T09:00:00Z")); // later file the same day wins

        MarginChange c = MarginChange.latest(rows, DEC).orElseThrow();

        assertThat(c.marginDate()).isEqualTo(D2);
        assertThat(c.previousDate()).isEqualTo(D1);
        assertThat(c.change()).isEqualByComparingTo("10");
        assertThat(c.exceeds(new BigDecimal("2"))).isTrue();
        assertThat(c.exceeds(new BigDecimal("10"))).isFalse();
    }

    @Test
    void needsTwoMarginDates() {
        assertThat(MarginChange.latest(List.of(margin(D1, DEC, "17.79", "2026-11-11T03:00:00Z")), DEC)).isEmpty();
        assertThat(MarginChange.latest(List.of(
                margin(D1, APR, "18.47", "2026-11-11T03:00:00Z"),
                margin(D2, APR, "18.47", "2026-11-12T03:00:00Z")), DEC)).isEmpty();
    }

    private static DailyMargin margin(LocalDate date, LocalDate expiry, String tm, String fetched) {
        return DailyMargin.from("TMCFGRNZM", new NcdexProductPage.Margin("Morning", date, expiry, null, null, null,
                new BigDecimal(tm), null, null, null, null), Instant.parse(fetched));
    }
}
