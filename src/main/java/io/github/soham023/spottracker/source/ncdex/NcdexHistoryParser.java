package io.github.soham023.spottracker.source.ncdex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.Prices;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the JSON behind NCDEX's history widgets:
 * <ul>
 *   <li>{@code /futureprices/filters}: {@code [{"exp_date":"18-Dec-2026",...}]}</li>
 *   <li>{@code /futureprices/get_data}: DataTables rows; numbers carry thousands separators, dates are
 *   {@code dd-MMM-yyyy} and the symbol cell is an {@code <a>} tag</li>
 *   <li>{@code /spotprices/price_graph}: parallel {@code dates} ("09-10-2026 16:42 pm") and {@code prices} arrays</li>
 * </ul>
 */
public class NcdexHistoryParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter CONTRACT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter GRAPH_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public List<LocalDate> parseExpiries(String json) throws IOException {
        List<LocalDate> out = new ArrayList<>();
        for (JsonNode n : JSON.readTree(json)) {
            String e = n.path("exp_date").asText("");
            if (!e.isBlank()) {
                out.add(NcdexDates.expiry(e));
            }
        }
        return out;
    }

    public List<PriceSnapshot> parseFutures(String json, String symbol, String commodity, String defaultCentre,
                                            Instant fetchedAt) throws IOException {
        JsonNode root = JSON.readTree(json);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new IOException("NCDEX futures history: no 'data' array (" + root.path("message").asText("?") + ")");
        }
        List<PriceSnapshot> out = new ArrayList<>();
        for (JsonNode r : data) {
            String rowSymbol = Jsoup.parse(r.path("symbol").asText("")).text().trim();
            if (!rowSymbol.isEmpty() && !rowSymbol.equalsIgnoreCase(symbol)) {
                continue;
            }
            BigDecimal close = Prices.parse(r.path("price_close").asText(null));
            if (close == null || close.signum() <= 0) {
                continue;
            }
            LocalDate date = NcdexDates.expiry(r.path("date").asText());
            LocalDate expiry = NcdexDates.expiry(r.path("expiry_date").asText());
            String centre = r.path("mkt_type").asText("").isBlank() ? defaultCentre : r.path("mkt_type").asText().trim();
            out.add(PriceSnapshot.futures(commodity, NcdexPriceSource.ID, centre, expiry.format(CONTRACT_MONTH), close,
                    date, fetchedAt).withRange(positive(r.path("price_low").asText(null)),
                    positive(r.path("price_high").asText(null))));
        }
        return out;
    }

    public List<PriceSnapshot> parseSpotGraph(String json, String commodity, String centre, String variety,
                                              Instant fetchedAt) throws IOException {
        JsonNode root = JSON.readTree(json);
        JsonNode dates = root.path("dates");
        JsonNode prices = root.path("prices");
        if (!dates.isArray() || !prices.isArray()) {
            throw new IOException("NCDEX spot history: missing 'dates'/'prices' arrays");
        }
        List<PriceSnapshot> out = new ArrayList<>();
        for (int i = 0; i < Math.min(dates.size(), prices.size()); i++) {
            if (!prices.get(i).isNumber()) {
                continue;
            }
            BigDecimal price = prices.get(i).decimalValue().setScale(2, RoundingMode.HALF_UP);
            if (price.signum() <= 0) {
                continue;
            }
            String raw = dates.get(i).asText().trim();
            LocalDate date = LocalDateTime.parse(raw.substring(0, Math.min(16, raw.length())), GRAPH_TIME).toLocalDate();
            out.add(PriceSnapshot.spot(commodity, NcdexPriceSource.ID, centre, variety, price, date, fetchedAt));
        }
        return out;
    }

    private static BigDecimal positive(String raw) {
        BigDecimal v = Prices.parse(raw);
        return v == null || v.signum() <= 0 ? null : v;
    }
}
