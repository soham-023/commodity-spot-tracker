package io.github.soham023.spottracker.spread;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-11-11T13:00:00Z");
    private static final LocalDate D1 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 11);

    private final SpreadCalculator calc = new SpreadCalculator(SpreadProperties.defaults());

    @Test
    void basisUsesNearMonthAndStaleSpotWithinLimit() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24954.10", LocalDate.of(2026, 11, 4)),
                spot("Nizamabad", "Unpolished", "23698.55", LocalDate.of(2026, 11, 4)),
                futures("2027-04", "28210", D2),
                futures("2026-12", "28560", D2));

        List<SpreadPoint> points = calc.compute("TURMERIC", data);

        assertThat(points).singleElement().satisfies(p -> {
            assertThat(p.kind()).isEqualTo(SpreadKind.BASIS);
            assertThat(p.label()).isEqualTo("2026-12");
            assertThat(p.spotPrice()).isEqualByComparingTo("24954.10");
            assertThat(p.spotDate()).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(p.spread()).isEqualByComparingTo("3605.90");
            assertThat(p.spreadPct()).isEqualByComparingTo("14.45");
            assertThat(p.flags()).containsExactly(SpreadFlag.WIDE_SPREAD);
        });
    }

    @Test
    void spotOlderThanLimitIsNotUsed() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24954.10", LocalDate.of(2026, 10, 1)),
                futures("2026-12", "28560", D2));
        assertThat(calc.compute("TURMERIC", data)).isEmpty();
    }

    @Test
    void mandiSpreadAveragesVarietiesAndStaysUnflaggedInsideThreshold() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", D2),
                mandi("Nizamabad", "Finger", "23400", D2),
                mandi("Nizamabad", "Bulb", "24000", D2));

        SpreadPoint p = calc.compute("TURMERIC", data).getFirst();

        assertThat(p.kind()).isEqualTo(SpreadKind.MANDI);
        assertThat(p.comparedPrice()).isEqualByComparingTo("23700");
        assertThat(p.spread()).isEqualByComparingTo("-300");
        assertThat(p.spreadPct()).isEqualByComparingTo("-1.25");
        assertThat(p.flags()).isEmpty();
        assertThat(p.priceMovePct()).isNull();
    }

    @Test
    void negativeSpreadBeyondThresholdIsFlagged() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "25000", D2),
                mandi("Warangal", "Finger", "22300", D2));
        SpreadPoint p = calc.compute("TURMERIC", data).getFirst();
        assertThat(p.spreadPct()).isEqualByComparingTo("-10.80");
        assertThat(p.flags()).contains(SpreadFlag.WIDE_SPREAD);
    }

    @Test
    void sharpDayOverDayMoveIsFlagged() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", D1),
                spot("Nizamabad", "Farmer Polished", "24000", D2),
                mandi("Erode", "Finger", "24100", D1),
                mandi("Erode", "Finger", "25000", D2));

        List<SpreadPoint> points = calc.compute("TURMERIC", data);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).flags()).isEmpty();
        assertThat(points.get(1).priceMovePct()).isEqualByComparingTo("3.73");
        assertThat(points.get(1).flags()).containsExactly(SpreadFlag.SHARP_MOVE);
    }

    @Test
    void customThresholdIsRespected() {
        var strict = new SpreadCalculator(new SpreadProperties(new BigDecimal("1"), null, null, null, null));
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", D2),
                mandi("Sangli", "Rajapuri", "24300", D2));
        assertThat(strict.compute("TURMERIC", data).getFirst().flags()).contains(SpreadFlag.WIDE_SPREAD);
        assertThat(calc.compute("TURMERIC", data).getFirst().flags()).isEmpty();
    }

    @Test
    void latestKeepsOnlyTheMostRecentDate() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", D1),
                futures("2026-12", "25000", D1),
                futures("2026-12", "25100", D2));
        assertThat(SpreadCalculator.latest(calc.compute("TURMERIC", data)))
                .extracting(SpreadPoint::date).containsOnly(D2);
    }

    private static PriceSnapshot spot(String market, String variety, String price, LocalDate d) {
        return PriceSnapshot.spot("TURMERIC", "NCDEX", market, variety, new BigDecimal(price), d, NOW);
    }

    private static PriceSnapshot futures(String month, String price, LocalDate d) {
        return PriceSnapshot.futures("TURMERIC", "NCDEX", "Nizamabad", month, new BigDecimal(price), d, NOW);
    }

    private static PriceSnapshot mandi(String market, String variety, String price, LocalDate d) {
        return PriceSnapshot.spot("TURMERIC", "AGMARKNET", market, variety, new BigDecimal(price), d, NOW);
    }
}
