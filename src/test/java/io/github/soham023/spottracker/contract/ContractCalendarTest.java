package io.github.soham023.spottracker.contract;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractCalendarTest {

    private static final LocalDate DEC = LocalDate.of(2026, 12, 18);
    private static final LocalDate APR = LocalDate.of(2027, 4, 16);
    private static final LocalDate MAY = LocalDate.of(2027, 5, 20);

    private final ContractCalendar calendar = new ContractCalendar(List.of(MAY, DEC, APR), 5);

    @Test
    void nearMonthIsTheFirstExpiryOutsideTheRollWindow() {
        assertThat(calendar.nearMonth(LocalDate.of(2026, 11, 11))).contains(DEC);
        assertThat(calendar.nearMonth(LocalDate.of(2026, 12, 12))).contains(DEC);   // 6 days left
        assertThat(calendar.nearMonth(LocalDate.of(2026, 12, 13))).contains(APR);   // 5 days left: rolled
        assertThat(calendar.nearMonth(LocalDate.of(2027, 5, 16))).isEmpty();
    }

    @Test
    void pickNearMonthSkipsAContractInsideItsRollWindow() {
        List<String> quoted = List.of("2027-04", "2026-12");
        assertThat(calendar.pickNearMonth(quoted, q -> q, LocalDate.of(2026, 12, 1))).contains("2026-12");
        assertThat(calendar.pickNearMonth(quoted, q -> q, LocalDate.of(2026, 12, 15))).contains("2027-04");
    }

    @Test
    void contractsTheCalendarDoesNotKnowStayTradable() {
        // history from before the product page was scraped: Sep/Oct contracts are not in the calendar
        assertThat(calendar.pickNearMonth(List.of("2026-10", "2026-12"), q -> q, LocalDate.of(2026, 9, 20)))
                .contains("2026-10");
        assertThat(ContractCalendar.empty().pickNearMonth(List.of("2027-04", "2026-12"), q -> q, DEC))
                .contains("2026-12");
    }

    @Test
    void rollWindowAndValidation() {
        assertThat(calendar.inRollWindow(DEC, LocalDate.of(2026, 12, 13))).isTrue();
        assertThat(calendar.inRollWindow(DEC, LocalDate.of(2026, 12, 19))).isFalse();
        assertThat(calendar.expiryOf("2027-05")).contains(MAY);
        assertThat(new ContractCalendar(List.of(DEC), 0).isTradable("2026-12", DEC)).isFalse();
        assertThatThrownBy(() -> new ContractCalendar(List.of(), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
