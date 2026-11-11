package io.github.soham023.spottracker.contract;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryMatrixTest {

    static final LocalDate DEC = LocalDate.of(2026, 12, 18);
    static final LocalDate APR = LocalDate.of(2027, 4, 16);

    /** The matrix published on the NCDEX product page on 2026-11-11 (Dec-26 and Apr-27). */
    public static DeliveryMatrix published() {
        return new DeliveryMatrix(List.of(
                e(DEC, "Unpolished Fingers", "Nizamabad", "-4", false),
                e(DEC, "Farmer polished fingers Nizamabad", "Nizamabad", "0", true),
                e(DEC, "Rajapore", "Sangli", "4", false),
                e(DEC, "Erode", "Erode", "0", false),
                e(DEC, "Salem", "Erode", "2", false),
                e(DEC, "Salem", "Basmat", "0", false),
                e(APR, "Farmer polished fingers Nizamabad", "Nizamabad", "0", true),
                e(APR, "Rajapore", "Sangli", "4", false),
                e(APR, "Erode", "Erode", "0", false),
                e(APR, "Salem", "Erode", "2", false),
                e(APR, "Salem", "Basmat", "0", false)));
    }

    private static DeliveryMatrix.Entry e(LocalDate x, String q, String c, String pct, boolean basis) {
        return new DeliveryMatrix.Entry(x, q, c, new BigDecimal(pct), basis);
    }

    private final DeliveryMatrix matrix = published();

    @Test
    void ncdexSpotGradesMapToTheirMatrixRow() {
        assertThat(matrix.forGrade(DEC, "Nizamabad", "Farmer Polished")).hasValueSatisfying(e -> assertThat(e.basis()).isTrue());
        assertThat(matrix.forGrade(DEC, "Nizamabad", "Unpolished")).map(e -> e.premiumPct().intValue()).contains(-4);
        assertThat(matrix.forGrade(DEC, "Sangli", "Rajapore")).map(e -> e.premiumPct().intValue()).contains(4);
    }

    @Test
    void unpolishedIsOnlyDeliverableAgainstDecember() {
        assertThat(matrix.forGrade(APR, "Nizamabad", "Unpolished")).isEmpty();
    }

    @Test
    void mandisUseTheVarietyOrASingleQualityCentre() {
        assertThat(matrix.forMandi(DEC, "Sangli", "Rajapuri")).map(e -> e.premiumPct().intValue()).contains(4);
        assertThat(matrix.forMandi(DEC, "Basmat", "Other")).map(e -> e.premiumPct().intValue()).contains(0);
        // Erode has two qualities (Erode 0%, Salem +2%) and "Finger" names neither: stay unadjusted
        assertThat(matrix.forMandi(DEC, "Erode", "Finger")).isEmpty();
        assertThat(matrix.forMandi(DEC, "Erode", "Salem")).map(e -> e.premiumPct().intValue()).contains(2);
        assertThat(matrix.forMandi(DEC, "Warangal", "Finger")).isEmpty();
    }

    @Test
    void unknownOrLaterExpiryFallsBackToTheNearestPublishedOne() {
        assertThat(matrix.forGrade(null, "Sangli", "Rajapore")).isPresent();
        assertThat(matrix.forGrade(LocalDate.of(2026, 10, 1), "Nizamabad", "Unpolished")).isPresent(); // -> Dec
        assertThat(matrix.forGrade(LocalDate.of(2027, 9, 1), "Sangli", "Rajapore")).isPresent();       // -> Dec (earliest)
        assertThat(DeliveryMatrix.empty().forGrade(DEC, "Sangli", "Rajapore")).isEmpty();
    }

    @Test
    void basisEquivalentDividesOutThePremium() {
        assertThat(DeliveryMatrix.toBasisEquivalent(new BigDecimal("28650"), new BigDecimal("4")))
                .isEqualByComparingTo("27548.08");
        assertThat(DeliveryMatrix.toBasisEquivalent(new BigDecimal("23698.55"), new BigDecimal("-4")))
                .isEqualByComparingTo("24685.99");
        assertThat(DeliveryMatrix.toBasisEquivalent(new BigDecimal("24954.10"), BigDecimal.ZERO))
                .isEqualByComparingTo("24954.10");
    }
}
