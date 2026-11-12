package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.HttpFetcher;
import io.github.soham023.spottracker.source.PriceSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** NCDEX polled spot prices (Live Spot Quotes) and turmeric futures (Live Quotes). */
@Component
public class NcdexPriceSource implements PriceSource {

    public static final String ID = "NCDEX";
    public static final String COMMODITY = "TURMERIC";

    private final NcdexProperties props;
    private final HttpFetcher http;
    private final Clock clock;
    private final NcdexSpotParser spotParser = new NcdexSpotParser();
    private final NcdexFuturesParser futuresParser = new NcdexFuturesParser();

    public NcdexPriceSource(NcdexProperties props, HttpFetcher http, Clock clock) {
        this.props = props;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        return props.enabled();
    }

    @Override
    public List<PriceSnapshot> fetch() throws Exception {
        Instant now = clock.instant();
        List<PriceSnapshot> all = new ArrayList<>();

        UriComponentsBuilder spotUrl = UriComponentsBuilder.fromUriString(props.baseUrl()).path("/markets/livespot");
        props.spotNames().forEach(n -> spotUrl.queryParam("symbol[]", n));
        String spotHtml = http.get(spotUrl.encode().build().toUriString(), props.timeout());
        all.addAll(spotParser.parse(spotHtml, now));

        String futuresHtml = http.get(props.baseUrl() + "/market-watch/live_quotes", props.timeout());
        all.addAll(futuresParser.parse(futuresHtml, props.futuresSymbol(), COMMODITY, props.basisCentre(), now));
        return all;
    }
}
