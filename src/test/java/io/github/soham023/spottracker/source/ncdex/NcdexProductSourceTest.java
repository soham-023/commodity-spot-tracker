package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.source.Fixtures;
import io.github.soham023.spottracker.source.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NcdexProductSourceTest {

    private final NcdexProperties ncdex = new NcdexProperties(true, null, null, null, null, null);
    private final NcdexProductProperties props = new NcdexProductProperties(true, null);

    @Test
    void readsTheProductPageOfTheConfiguredFuturesSymbol() throws Exception {
        HttpFetcher http = mock(HttpFetcher.class);
        when(http.get(eq("https://www.ncdex.com/products/TMCFGRNZM"), any()))
                .thenReturn(Fixtures.read("ncdex-product-turmeric.html"));

        NcdexProductPage page = new NcdexProductSource(ncdex, props, http).fetch();

        assertThat(page.symbol()).isEqualTo("TMCFGRNZM");
        assertThat(page.expiries()).hasSize(3);
        assertThat(page.margins()).hasSize(3);
    }

    @Test
    void transportErrorsPropagate() throws Exception {
        HttpFetcher http = mock(HttpFetcher.class);
        when(http.get(any(), any())).thenThrow(new IOException("GET https://www.ncdex.com/products/TMCFGRNZM returned HTTP 403"));
        assertThatThrownBy(() -> new NcdexProductSource(ncdex, props, http).fetch()).hasMessageContaining("403");
    }
}
