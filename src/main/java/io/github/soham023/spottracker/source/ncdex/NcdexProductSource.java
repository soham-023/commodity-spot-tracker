package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.source.HttpFetcher;
import org.springframework.stereotype.Component;

/** Fetches and parses the NCDEX product page for the configured futures symbol. */
@Component
public class NcdexProductSource {

    public static final String ID = "NCDEX_PRODUCT";

    private final NcdexProperties ncdex;
    private final NcdexProductProperties props;
    private final HttpFetcher http;
    private final NcdexProductParser parser = new NcdexProductParser();

    public NcdexProductSource(NcdexProperties ncdex, NcdexProductProperties props, HttpFetcher http) {
        this.ncdex = ncdex;
        this.props = props;
        this.http = http;
    }

    public boolean isEnabled() {
        return props.enabled();
    }

    public String symbol() {
        return ncdex.futuresSymbol();
    }

    public String productUrl() {
        return ncdex.baseUrl() + "/products/" + ncdex.futuresSymbol();
    }

    public NcdexProductPage fetch() throws Exception {
        return parser.parse(http.get(productUrl(), props.timeout()), ncdex.futuresSymbol());
    }
}
