package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.HistoricalPriceSource;
import io.github.soham023.spottracker.source.HttpFetcher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Polled spot history from NCDEX "Spot Prices" ({@code /spotprices/price_graph}) for the three turmeric
 * series. NCDEX only serves roughly the last 30 days; older dates come back empty.
 */
@Component
public class NcdexSpotHistorySource implements HistoricalPriceSource {

    public static final String ID = "NCDEX_SPOT_HISTORY";
    static final String PAGE = "/markets/spotprices";

    /** NCDEX spot symbol → (centre, variety) as the live spot page labels them. */
    record Series(String symbol, String centre, String variety) {
    }

    static final List<Series> SERIES = List.of(
            new Series("TMCFGRNZM", "Nizamabad", "Farmer Polished"),
            new Series("TMCFUPNZM", "Nizamabad", "Unpolished"),
            new Series("TMCRJPSGL", "Sangli", "Rajapore"));

    private final NcdexProperties props;
    private final HttpFetcher http;
    private final Clock clock;
    private final NcdexHistoryParser parser = new NcdexHistoryParser();

    public NcdexSpotHistorySource(NcdexProperties props, HttpFetcher http, Clock clock) {
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
    public List<PriceSnapshot> fetchRange(LocalDate from, LocalDate to) throws Exception {
        Instant now = clock.instant();
        NcdexAjax ajax = new NcdexAjax(http, props.baseUrl(), props.timeout(), PAGE);
        List<PriceSnapshot> out = new ArrayList<>();
        for (Series s : SERIES) {
            String json = ajax.post("/spotprices/price_graph", Map.of("symbol", s.symbol(), "ftype", "1",
                    "df", from.toString(), "dt", to.toString()));
            for (PriceSnapshot p : parser.parseSpotGraph(json, NcdexPriceSource.COMMODITY, s.centre(), s.variety(), now)) {
                if (!p.getPriceDate().isBefore(from) && !p.getPriceDate().isAfter(to)) {
                    out.add(p);
                }
            }
        }
        return out;
    }
}
