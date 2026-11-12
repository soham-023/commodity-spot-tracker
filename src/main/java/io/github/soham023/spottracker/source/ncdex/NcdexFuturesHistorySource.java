package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.HistoricalPriceSource;
import io.github.soham023.spottracker.source.HttpFetcher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily OHLC history for every turmeric futures expiry from NCDEX "Futures Prices"
 * ({@code /markets/futureprices}). Only expiries that can have traded inside the range are requested.
 */
@Component
public class NcdexFuturesHistorySource implements HistoricalPriceSource {

    public static final String ID = "NCDEX_FUTURES_HISTORY";
    static final String PAGE = "/markets/futureprices";
    private static final String[] COLUMNS = {"symbol", "expiry_date", "date", "prev_close", "price_open", "price_high",
            "price_low", "price_close", "price_vol", "open_int", "tot_traded_value", "mkt_type"};
    /** Turmeric contracts list roughly 6 months before expiry; ask a little wider. */
    private static final int LISTING_MONTHS = 8;

    private final NcdexProperties props;
    private final HttpFetcher http;
    private final Clock clock;
    private final NcdexHistoryParser parser = new NcdexHistoryParser();

    public NcdexFuturesHistorySource(NcdexProperties props, HttpFetcher http, Clock clock) {
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
        String symbol = props.futuresSymbol();
        List<LocalDate> expiries = parser.parseExpiries(ajax.post("/futureprices/filters", Map.of(
                "select", "symbol", "value", symbol, "dependent", "expiry_date", "instr_name", "FUTCOM")));
        List<PriceSnapshot> out = new ArrayList<>();
        for (LocalDate expiry : expiries) {
            if (expiry.isBefore(from) || expiry.minusMonths(LISTING_MONTHS).isAfter(to)) {
                continue;
            }
            Map<String, String> params = dataTablesParams();
            params.put("instr_name", "FUTCOM");
            params.put("symbol", symbol);
            params.put("expiry_date", expiry.format(NcdexDates.EXPIRY_FMT));
            String json = ajax.post("/futureprices/get_data", params);
            for (PriceSnapshot s : parser.parseFutures(json, symbol, NcdexPriceSource.COMMODITY, props.basisCentre(), now)) {
                if (!s.getPriceDate().isBefore(from) && !s.getPriceDate().isAfter(to)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** The DataTables request the page sends; get_data fails with a PHP error without the column list. */
    static Map<String, String> dataTablesParams() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("draw", "1");
        for (int i = 0; i < COLUMNS.length; i++) {
            String c = "columns[" + i + "]";
            p.put(c + "[data]", COLUMNS[i]);
            p.put(c + "[name]", "");
            p.put(c + "[searchable]", "true");
            p.put(c + "[orderable]", "true");
            p.put(c + "[search][value]", "");
            p.put(c + "[search][regex]", "false");
        }
        p.put("order[0][column]", "2");
        p.put("order[0][dir]", "desc");
        p.put("start", "0");
        p.put("length", "2000"); // -1 ("all") makes the server emit invalid SQL
        p.put("search[value]", "");
        p.put("search[regex]", "false");
        return p;
    }
}
