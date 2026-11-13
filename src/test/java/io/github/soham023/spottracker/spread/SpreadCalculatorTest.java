package io.github.soham023.spottracker.spread;

import io.github.soham023.spottracker.contract.ContractCalendar;
import io.github.soham023.spottracker.contract.DeliveryMatrix;
import io.github.soham023.spottracker.contract.DeliveryMatrixHistory;
import io.github.soham023.spottracker.contract.DeliveryMatrixTest;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-11-11T13:00:00Z");
    private static final LocalDate D1 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 11);

    private static final ContractCalendar CALENDAR = new ContractCalendar(
            List.of(LocalDate.of(2026, 12, 18), LocalDate.of(2027, 4, 16), LocalDate.of(2027, 5, 20)), 5);

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
    void basisRollsToTheNextContractInsideTheRollWindow() {
        var calendar = new ContractCalendar(List.of(LocalDate.of(2026, 12, 18), LocalDate.of(2027, 4, 16)), 5);
        LocalDate early = LocalDate.of(2026, 12, 10);
        LocalDate late = LocalDate.of(2026, 12, 14);
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", early),
                spot("Nizamabad", "Farmer Polished", "24000", late),
                futures("2026-12", "24500", early), futures("2027-04", "25500", early),
                futures("2026-12", "24100", late), futures("2027-04", "25400", late));

        List<SpreadPoint> points = calc.compute("TURMERIC", data, calendar);

        assertThat(points).extracting(SpreadPoint::date, SpreadPoint::label).containsExactly(
                org.assertj.core.groups.Tuple.tuple(early, "2026-12"),
                org.assertj.core.groups.Tuple.tuple(late, "2027-04"));
        // without the calendar the expiring December contract would still be used
        assertThat(calc.compute("TURMERIC", data)).extracting(SpreadPoint::label).containsOnly("2026-12");
    }

    @Test
    void rajaporeMandiPremiumDoesNotRaiseAFalseWideSpread() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", D2),
                mandi("Sangli", "Rajapuri", "25500", D2));

        SpreadPoint raw = calc.compute("TURMERIC", data).getFirst();
        SpreadPoint adjusted = calc.compute("TURMERIC", data, CALENDAR, DeliveryMatrixTest.published()).getFirst();

        assertThat(raw.spreadPct()).isEqualByComparingTo("6.25");
        assertThat(raw.flags()).containsExactly(SpreadFlag.WIDE_SPREAD);
        assertThat(raw.adjustedSpreadPct()).isNull();

        assertThat(adjusted.spreadPct()).isEqualByComparingTo("6.25");          // raw kept
        assertThat(adjusted.adjustmentPct()).isEqualByComparingTo("4");
        assertThat(adjusted.adjustedSpread()).isEqualByComparingTo("519.23");   // 25500 / 1.04 - 24000
        assertThat(adjusted.adjustedSpreadPct()).isEqualByComparingTo("2.16");
        assertThat(adjusted.flags()).isEmpty();
    }

    @Test
    void deliveryBasisConvertsEachNcdexGradeToBasisEquivalent() {
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", D2),
                spot("Nizamabad", "Unpolished", "23040", D2),
                spot("Sangli", "Rajapore", "28650", D2),
                spot("Hingoli", "Other", "22000", D2),
                futures("2026-12", "24500", D2));

        List<SpreadPoint> points = calc.compute("TURMERIC", data, CALENDAR, DeliveryMatrixTest.published());

        // Hingoli is not a delivery centre, so it gets no DELIVERY point
        assertThat(points).extracting(SpreadPoint::kind, SpreadPoint::label).containsExactly(
                org.assertj.core.groups.Tuple.tuple(SpreadKind.BASIS, "2026-12"),
                org.assertj.core.groups.Tuple.tuple(SpreadKind.DELIVERY, "Rajapore @ Sangli"),
                org.assertj.core.groups.Tuple.tuple(SpreadKind.DELIVERY, "Unpolished @ Nizamabad"));
        SpreadPoint basis = points.get(0);
        assertThat(basis.adjustmentPct()).isEqualByComparingTo("0");
        assertThat(basis.adjustedSpreadPct()).isEqualByComparingTo(basis.spreadPct());

        SpreadPoint unpolished = points.get(2);                                // 23040 / 0.96 = 24000
        assertThat(unpolished.spreadPct()).isEqualByComparingTo("6.34");
        assertThat(unpolished.adjustedSpread()).isEqualByComparingTo("500.00");
        assertThat(unpolished.adjustedSpreadPct()).isEqualByComparingTo("2.08");
        assertThat(unpolished.flags()).isEmpty();

        SpreadPoint rajapore = points.get(1);                                  // 28650 / 1.04 = 27548.08
        assertThat(rajapore.spread()).isEqualByComparingTo("-4150");
        assertThat(rajapore.adjustedSpread()).isEqualByComparingTo("-3048.08");
        assertThat(rajapore.adjustedSpreadPct()).isEqualByComparingTo("-11.06");
        assertThat(rajapore.flags()).containsExactly(SpreadFlag.WIDE_SPREAD);
        assertThat(rajapore.priceMovePct()).isNull();
    }

    @Test
    void historyIsAdjustedWithTheMatrixInForceThatDayNotTodays() {
        // NCDEX widened the Unpolished discount from -4% to -8% on D2
        LocalDate dec = LocalDate.of(2026, 12, 18);
        DeliveryMatrix before = new DeliveryMatrix(List.of(
                new DeliveryMatrix.Entry(dec, "Farmer polished fingers Nizamabad", "Nizamabad", BigDecimal.ZERO, true),
                new DeliveryMatrix.Entry(dec, "Unpolished Fingers", "Nizamabad", new BigDecimal("-4"), false)));
        DeliveryMatrix after = new DeliveryMatrix(List.of(
                new DeliveryMatrix.Entry(dec, "Farmer polished fingers Nizamabad", "Nizamabad", BigDecimal.ZERO, true),
                new DeliveryMatrix.Entry(dec, "Unpolished Fingers", "Nizamabad", new BigDecimal("-8"), false)));
        List<PriceSnapshot> data = List.of(
                spot("Nizamabad", "Farmer Polished", "24000", D1),
                spot("Nizamabad", "Unpolished", "23040", D1),
                futures("2026-12", "24500", D1),
                spot("Nizamabad", "Farmer Polished", "24000", D2),
                spot("Nizamabad", "Unpolished", "23040", D2),
                futures("2026-12", "24500", D2));

        List<SpreadPoint> delivery = calc.compute("TURMERIC", data, CALENDAR,
                        DeliveryMatrixHistory.of(Map.of(D1, before, D2, after))).stream()
                .filter(p -> p.kind() == SpreadKind.DELIVERY).toList();

        assertThat(delivery).extracting(SpreadPoint::date, SpreadPoint::adjustmentPct).satisfiesExactly(
                t -> assertThat(t.toList()).containsExactly(D1, new BigDecimal("-4")),
                t -> assertThat(t.toList()).containsExactly(D2, new BigDecimal("-8")));
        assertThat(delivery.get(0).adjustedSpread()).isEqualByComparingTo("500.00");   // 23040 / 0.96 = 24000
        assertThat(delivery.get(1).adjustedSpread()).isEqualByComparingTo("-543.48");  // 23040 / 0.92 = 25043.48

        // The old behaviour, today's matrix applied to all history, would have used -8% on D1 as well
        List<SpreadPoint> withTodays = calc.compute("TURMERIC", data, CALENDAR, after).stream()
                .filter(p -> p.kind() == SpreadKind.DELIVERY && p.date().equals(D1)).toList();
        assertThat(withTodays.getFirst().adjustmentPct()).isEqualByComparingTo("-8");
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
