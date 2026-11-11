package io.github.soham023.spottracker.source.agmarknet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.Prices;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses a data.gov.in mandi price page. Prices have been served both as JSON numbers and as strings
 * over the dataset's lifetime, so both are accepted. The modal price is the representative price.
 */
public class DataGovMandiParser {

    private static final DateTimeFormatter ARRIVAL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ObjectMapper mapper;

    public DataGovMandiParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Page(int total, List<PriceSnapshot> snapshots) {
    }

    public Page parse(String json, List<String> marketFilter, Instant fetchedAt) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (root.has("status") && !"ok".equalsIgnoreCase(root.path("status").asText())) {
            throw new IOException("data.gov.in returned status " + root.path("status").asText()
                    + ": " + root.path("message").asText());
        }
        List<PriceSnapshot> out = new ArrayList<>();
        for (JsonNode r : root.path("records")) {
            String market = r.path("market").asText("").trim();
            if (market.isEmpty() || !matches(market, marketFilter)) {
                continue;
            }
            BigDecimal modal = Prices.parse(r.path("modal_price").asText(null));
            if (modal == null || modal.signum() <= 0) {
                continue;
            }
            LocalDate date = LocalDate.parse(r.path("arrival_date").asText(), ARRIVAL);
            String commodity = r.path("commodity").asText().trim().toUpperCase(Locale.ROOT);
            String variety = r.path("variety").asText("").trim();
            out.add(PriceSnapshot.spot(commodity, DataGovMandiSource.ID, market, variety, modal, date, fetchedAt)
                    .withRange(Prices.parse(r.path("min_price").asText(null)),
                            Prices.parse(r.path("max_price").asText(null))));
        }
        return new Page(root.path("total").asInt(out.size()), out);
    }

    static boolean matches(String market, List<String> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        String m = market.toLowerCase(Locale.ROOT);
        return filter.stream().anyMatch(f -> m.contains(f.toLowerCase(Locale.ROOT).trim()));
    }
}
