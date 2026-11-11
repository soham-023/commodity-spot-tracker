package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.Prices;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses NCDEX "Live Spot Quotes" ({@code /markets/livespot}). Each row is one polled spot price:
 * product name, Hindi name, centre, centre (Hindi), time of polling, price, % change.
 * The price date is the polling date, not the page date: NCDEX can show a stale poll for days.
 */
public class NcdexSpotParser {

    private static final Pattern NAME = Pattern.compile("^([^(]+?)\\s*(?:\\(([^)]+)\\))?$");

    public List<PriceSnapshot> parse(String html, Instant fetchedAt) {
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table.quote-table > tbody > tr");
        List<PriceSnapshot> result = new ArrayList<>();
        for (Element row : rows) {
            Elements cells = row.select("> td");
            if (cells.size() < 6) {
                continue;
            }
            String product = cells.get(0).text().trim();
            Matcher name = NAME.matcher(product);
            if (!name.matches()) {
                continue;
            }
            BigDecimal price = Prices.parse(cells.get(5).text());
            if (price == null) {
                continue;
            }
            String commodity = name.group(1).trim().toUpperCase(Locale.ROOT);
            String variety = name.group(2) == null ? PriceSnapshot.NO_VARIETY : name.group(2).trim();
            String centre = cells.get(2).text().trim();
            var polled = NcdexDates.pollingTime(cells.get(4).text());
            result.add(PriceSnapshot.spot(commodity, NcdexPriceSource.ID, centre, variety, price,
                    polled.toLocalDate(), fetchedAt));
        }
        return result;
    }
}
