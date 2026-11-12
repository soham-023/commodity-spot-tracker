package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.source.Fixtures;
import io.github.soham023.spottracker.source.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Parsers and sources for the NCDEX history endpoints, on responses recorded 2026-11-12. */
class NcdexHistoryTest {

    private static final Instant FETCHED = Instant.parse("2026-11-12T06:00:00Z");
    private static final String PAGE_HTML = "<html><head><meta name=\"csrf-token\" content=\"tok123\"></head></html>";
    private final NcdexHistoryParser parser = new NcdexHistoryParser();
    private final Clock clock = Clock.fixed(FETCHED, ZoneId.of("Asia/Kolkata"));
    private final NcdexProperties props = new NcdexProperties(true, null, null, null, null, null);

    @Test
    void parsesExpiryList() throws Exception {
        List<LocalDate> expiries = parser.parseExpiries(Fixtures.read("ncdex-futureprices-expiries.json"));
        assertThat(expiries).hasSize(34).contains(LocalDate.of(2022, 4, 20), LocalDate.of(2026, 12, 18),
                LocalDate.of(2027, 5, 20));
    }

    @Test
    void parsesFuturesRowsStrippingCommasAndSymbolLink() throws Exception {
        List<PriceSnapshot> rows = parser.parseFutures(Fixtures.read("ncdex-futureprices-TMCFGRNZM-18-Dec-2026.json"),
                "TMCFGRNZM", "TURMERIC", "Nizamabad", FETCHED);
        assertThat(rows).extracting(PriceSnapshot::getPriceDate, p -> p.getPrice().toPlainString())
                .containsExactly(
                        tuple(LocalDate.of(2026, 11, 11), "28868.00"),
                        tuple(LocalDate.of(2026, 11, 9), "28414.00"),
                        tuple(LocalDate.of(2026, 11, 8), "28348.00"),
                        tuple(LocalDate.of(2026, 11, 6), "28026.00"),
                        tuple(LocalDate.of(2026, 11, 5), "27572.00"));
        PriceSnapshot nov9 = rows.get(1);
        assertThat(nov9.getPriceType()).isEqualTo(PriceType.FUTURES);
        assertThat(nov9.getContractMonth()).isEqualTo("2026-12");
        assertThat(nov9.getMarket()).isEqualTo("Nizamabad");
        assertThat(nov9.getSource()).isEqualTo("NCDEX");
        assertThat(nov9.getMinPrice()).isEqualByComparingTo("28050");
        assertThat(nov9.getMaxPrice()).isEqualByComparingTo("28448");
    }

    @Test
    void futuresServerErrorIsReported() {
        assertThatThrownBy(() -> parser.parseFutures("{\"message\":\"Undefined array key \\\"\\\"\"}", "TMCFGRNZM",
                "TURMERIC", "Nizamabad", FETCHED)).isInstanceOf(IOException.class).hasMessageContaining("Undefined");
    }

    @Test
    void parsesSpotGraph() throws Exception {
        List<PriceSnapshot> rows = parser.parseSpotGraph(Fixtures.read("ncdex-spot-price-graph-TMCFGRNZM.json"),
                "TURMERIC", "Nizamabad", "Farmer Polished", FETCHED);
        assertThat(rows).hasSize(17).allMatch(r -> r.getPriceType() == PriceType.SPOT);
        assertThat(rows.get(0).getPriceDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(rows.get(0).getPrice()).isEqualByComparingTo("21378.40");
        assertThat(rows.get(15).getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(rows.get(15).getPrice()).isEqualByComparingTo("24954.10");
        assertThat(rows.get(16).getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(rows.get(16).getPrice()).isEqualByComparingTo("27144.85");
    }

    @Test
    void csrfTokenComesFromThePageMetaTag() {
        assertThat(NcdexAjax.csrfToken(PAGE_HTML)).isEqualTo("tok123");
        assertThatThrownBy(() -> NcdexAjax.csrfToken("<html></html>")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void futuresSourceRequestsOnlyContractsTradingInRange() throws Exception {
        HttpFetcher http = mock(HttpFetcher.class);
        when(http.get(eq("https://www.ncdex.com/markets/futureprices"), any())).thenReturn(PAGE_HTML);
        when(http.postForm(eq("https://www.ncdex.com/futureprices/filters"), anyMap(), anyString(), any()))
                .thenReturn(Fixtures.read("ncdex-futureprices-expiries.json"));
        when(http.postForm(eq("https://www.ncdex.com/futureprices/get_data"), anyMap(), anyString(), any()))
                .thenReturn(Fixtures.read("ncdex-futureprices-TMCFGRNZM-18-Dec-2026.json"));

        var rows = new NcdexFuturesHistorySource(props, http, clock)
                .fetchRange(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 11));

        verify(http).postForm(eq("https://www.ncdex.com/futureprices/get_data"), argThat((Map<String, String> m) ->
                "18-Dec-2026".equals(m.get("expiry_date")) && "tok123".equals(m.get("_token"))
                        && "price_close".equals(m.get("columns[7][data]"))), eq("https://www.ncdex.com/markets/futureprices"), any());
        verify(http, never()).postForm(eq("https://www.ncdex.com/futureprices/get_data"),
                argThat((Map<String, String> m) -> "20-Oct-2026".equals(m.get("expiry_date"))), anyString(), any());
        // fixture returned for each requested expiry; only rows inside the window are kept
        assertThat(rows).isNotEmpty().allMatch(r -> !r.getPriceDate().isBefore(LocalDate.of(2026, 11, 6)));
    }

    @Test
    void spotSourceAsksForAllThreeSeries() throws Exception {
        HttpFetcher http = mock(HttpFetcher.class);
        when(http.get(eq("https://www.ncdex.com/markets/spotprices"), any())).thenReturn(PAGE_HTML);
        when(http.postForm(eq("https://www.ncdex.com/spotprices/price_graph"), anyMap(), anyString(), any()))
                .thenReturn(Fixtures.read("ncdex-spot-price-graph-TMCFGRNZM.json"));

        var rows = new NcdexSpotHistorySource(props, http, clock)
                .fetchRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12));

        for (String symbol : List.of("TMCFGRNZM", "TMCFUPNZM", "TMCRJPSGL")) {
            verify(http).postForm(eq("https://www.ncdex.com/spotprices/price_graph"), argThat((Map<String, String> m) ->
                    symbol.equals(m.get("symbol")) && "2026-11-01".equals(m.get("df")) && "2026-11-12".equals(m.get("dt"))),
                    anyString(), any());
        }
        assertThat(rows).extracting(PriceSnapshot::getVariety).contains("Farmer Polished", "Unpolished", "Rajapore");
        assertThat(rows).allMatch(r -> !r.getPriceDate().isBefore(LocalDate.of(2026, 11, 1)));
    }
}
