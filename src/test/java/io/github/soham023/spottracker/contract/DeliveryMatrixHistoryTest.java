package io.github.soham023.spottracker.contract;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryMatrixHistoryTest {

    private static final LocalDate DEC = LocalDate.of(2026, 12, 18);
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    private static DeliveryMatrix unpolishedAt(String pct) {
        return new DeliveryMatrix(List.of(
                new DeliveryMatrix.Entry(DEC, "Farmer polished fingers Nizamabad", "Nizamabad", BigDecimal.ZERO, true),
                new DeliveryMatrix.Entry(DEC, "Unpolished Fingers", "Nizamabad", new BigDecimal(pct), false)));
    }

    private static int unpolished(DeliveryMatrix m) {
        return m.forGrade(DEC, "Nizamabad", "Unpolished").orElseThrow().premiumPct().intValue();
    }

    private final DeliveryMatrixHistory history = DeliveryMatrixHistory.of(Map.of(
            OCT_1, unpolishedAt("-3"),
            NOV_1, unpolishedAt("-4")));

    @Test
    void eachDateUsesTheMatrixPublishedOnOrBeforeIt() {
        assertThat(unpolished(history.on(OCT_1))).isEqualTo(-3);
        assertThat(unpolished(history.on(LocalDate.of(2026, 10, 31)))).isEqualTo(-3);
        assertThat(unpolished(history.on(NOV_1))).isEqualTo(-4);
        assertThat(unpolished(history.on(LocalDate.of(2026, 11, 20)))).isEqualTo(-4);
    }

    @Test
    void datesBeforeTheFirstScrapeFallBackToTheEarliestMatrix() {
        assertThat(unpolished(history.on(LocalDate.of(2024, 3, 1)))).isEqualTo(-3);
        assertThat(unpolished(history.on(null))).isEqualTo(-3);
    }

    @Test
    void constantAndEmptyHistories() {
        DeliveryMatrixHistory constant = DeliveryMatrixHistory.constant(unpolishedAt("-4"));
        assertThat(unpolished(constant.on(LocalDate.of(2022, 1, 3)))).isEqualTo(-4);
        assertThat(unpolished(constant.on(LocalDate.of(2027, 1, 1)))).isEqualTo(-4);

        assertThat(DeliveryMatrixHistory.empty().on(NOV_1).isEmpty()).isTrue();
        assertThat(DeliveryMatrixHistory.constant(DeliveryMatrix.empty()).isEmpty()).isTrue();
        assertThat(DeliveryMatrixHistory.of(Map.of(NOV_1, DeliveryMatrix.empty())).isEmpty()).isTrue();
    }
}
