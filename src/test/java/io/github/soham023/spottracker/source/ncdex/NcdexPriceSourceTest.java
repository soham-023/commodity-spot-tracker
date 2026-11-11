package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.source.Fixtures;
import io.github.soham023.spottracker.source.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NcdexPriceSourceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T13:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private final NcdexProperties props = new NcdexProperties(true, null, null, null, null, null);

    @Test
    void combinesSpotAndFuturesPages() throws Exception {
        HttpFetcher http = mock(HttpFetcher.class);
        when(http.get(argThat(u -> u != null && u.contains("/markets/livespot")
                && u.contains("symbol%5B%5D=Turmeric%20(Farmer%20Polished)")), any()))
                .thenReturn(Fixtures.read("ncdex-live-spot-turmeric.html"));
        when(http.get(argThat(u -> u != null && u.endsWith("/market-watch/live_quotes")), any()))
                .thenReturn(Fixtures.read("ncdex-live-quotes.html"));

        var prices = new NcdexPriceSource(props, http, clock).fetch();

        assertThat(prices).hasSize(5);
        assertThat(prices).filteredOn(p -> p.getPriceType() == PriceType.FUTURES).hasSize(2);
        assertThat(prices).allSatisfy(p -> assertThat(p.getFetchedAt()).isEqualTo(clock.instant()));
    }

    @Test
    void transportErrorsPropagateToTheIngestionService() throws Exception {
        HttpFetcher http = mock(HttpFetcher.class);
        when(http.get(any(), any())).thenThrow(new IOException("GET https://www.ncdex.com returned HTTP 403"));

        assertThatThrownBy(() -> new NcdexPriceSource(props, http, clock).fetch()).hasMessageContaining("403");
    }

    @Test
    void redactsSecretsFromUrls() {
        assertThat(HttpFetcher.redact("https://api.data.gov.in/resource/x?api-key=abc123&format=json"))
                .isEqualTo("https://api.data.gov.in/resource/x?api-key=***&format=json");
        assertThat(HttpFetcher.redact("https://api.telegram.org/bot123:ABC/sendMessage"))
                .isEqualTo("https://api.telegram.org/bot***/sendMessage");
    }
}
