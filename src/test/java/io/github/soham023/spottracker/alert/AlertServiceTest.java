package io.github.soham023.spottracker.alert;

import io.github.soham023.spottracker.spread.SpreadFlag;
import io.github.soham023.spottracker.spread.SpreadKind;
import io.github.soham023.spottracker.spread.SpreadPoint;
import io.github.soham023.spottracker.spread.SpreadService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T13:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private final SpreadPoint wideBasis = new SpreadPoint("TURMERIC", DAY, SpreadKind.BASIS, "2026-12",
            new BigDecimal("24954.10"), LocalDate.of(2026, 11, 4), new BigDecimal("28560"),
            new BigDecimal("3605.90"), new BigDecimal("14.45"), null, Set.of(SpreadFlag.WIDE_SPREAD));
    private final SpreadPoint calmMandi = new SpreadPoint("TURMERIC", DAY, SpreadKind.MANDI, "Erode",
            new BigDecimal("24954.10"), LocalDate.of(2026, 11, 4), new BigDecimal("24350"),
            new BigDecimal("-604.10"), new BigDecimal("-2.42"), null, Set.of());

    @Test
    void sameFlagIsAlertedOnlyOncePerDay() throws Exception {
        SpreadService spreads = mock(SpreadService.class);
        when(spreads.spreads(eq("TURMERIC"), any(), any())).thenReturn(List.of(wideBasis, calmMandi));
        AlertRepository repo = mock(AlertRepository.class);
        Set<String> stored = new HashSet<>();
        when(repo.existsByDedupeKey(anyString())).thenAnswer(inv -> stored.contains(inv.<String>getArgument(0)));
        when(repo.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            stored.add(a.getDedupeKey());
            return a;
        });
        AlertChannel channel = mock(AlertChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.name()).thenReturn("test");
        AlertChannel disabled = mock(AlertChannel.class);

        AlertService service = new AlertService(spreads, repo, List.of(channel, disabled), clock);
        List<Alert> first = service.evaluate("TURMERIC");
        List<Alert> second = service.evaluate("TURMERIC");

        assertThat(first).singleElement().satisfies(a -> {
            assertThat(a.getDedupeKey()).isEqualTo("TURMERIC|2026-11-11|BASIS|2026-12|WIDE_SPREAD");
            assertThat(a.getDeliveredTo()).isEqualTo("test");
            assertThat(a.getMessage()).contains("+14.45%").contains("₹3,605.9").contains("polled 2026-11-04");
        });
        assertThat(second).isEmpty();
        verify(channel, times(1)).send(any());
        verify(disabled, never()).send(any());
    }

    @Test
    void failingChannelDoesNotBlockOthers() throws Exception {
        SpreadService spreads = mock(SpreadService.class);
        when(spreads.spreads(eq("TURMERIC"), any(), any())).thenReturn(List.of(wideBasis));
        AlertRepository repo = mock(AlertRepository.class);
        when(repo.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        AlertChannel broken = mock(AlertChannel.class);
        when(broken.isEnabled()).thenReturn(true);
        when(broken.name()).thenReturn("telegram");
        doThrow(new RuntimeException("401 Unauthorized")).when(broken).send(any());
        AlertChannel log = new LogAlertChannel();

        List<Alert> created = new AlertService(spreads, repo, List.of(broken, log), clock).evaluate("TURMERIC");

        assertThat(created.getFirst().getDeliveredTo()).isEqualTo("log");
    }

    @Test
    void sharpMoveMessageShowsTheMove() {
        SpreadPoint move = new SpreadPoint("TURMERIC", DAY, SpreadKind.MANDI, "Sangli", new BigDecimal("24000"), DAY,
                new BigDecimal("26900"), new BigDecimal("2900"), new BigDecimal("12.08"), new BigDecimal("-4.10"),
                Set.of(SpreadFlag.SHARP_MOVE));
        assertThat(AlertService.message(move, SpreadFlag.SHARP_MOVE))
                .startsWith("Turmeric at Sangli mandi vs NCDEX spot on 2026-11-11: price moved -4.10% day over day.");
    }
}
