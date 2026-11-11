package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.Prices;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses NCDEX "Live Quotes" ({@code /market-watch/live_quotes}). Rows carry the symbol and expiry
 * only in the chart link ({@code graph_price('TMCFGRNZM','2026-12-18','FUTCOM')}), so that is what
 * identifies a contract. Price is the last traded price (falls back to previous close when no trade).
 */
public class NcdexFuturesParser {

    private static final Pattern GRAPH = Pattern.compile("graph_price\\('([A-Z0-9]+)','(\\d{4}-\\d{2}-\\d{2})'");
    private static final DateTimeFormatter CONTRACT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** One parsed futures row, kept separately so callers can pick the near month. */
    public record FuturesQuote(String symbol, LocalDate expiry, BigDecimal ltp, BigDecimal low, BigDecimal high,
                               BigDecimal previousClose, BigDecimal exchangeSpot) {
    }

    public List<FuturesQuote> parseQuotes(String html, String symbol) {
        Document doc = Jsoup.parse(html);
        List<FuturesQuote> quotes = new ArrayList<>();
        for (Element row : doc.select("table.quote-table > tbody > tr")) {
            Element link = row.selectFirst("td.graph a[onclick]");
            if (link == null) {
                continue;
            }
            Matcher m = GRAPH.matcher(link.attr("onclick"));
            if (!m.find() || !m.group(1).equalsIgnoreCase(symbol)) {
                continue;
            }
            var right = row.select("> td.right");
            // Order of td.right cells: open, prev close, change, change%, ATP, spot, best buy, best sell, OI
            BigDecimal previousClose = right.size() > 1 ? Prices.parse(right.get(1).text()) : null;
            BigDecimal exchangeSpot = right.size() > 5 ? Prices.parse(right.get(5).text()) : null;
            quotes.add(new FuturesQuote(m.group(1), LocalDate.parse(m.group(2)),
                    Prices.parse(text(row, "li.ltp")), Prices.parse(text(row, "li.low")),
                    Prices.parse(text(row, "li.high")), previousClose, exchangeSpot));
        }
        return quotes;
    }

    public List<PriceSnapshot> parse(String html, String symbol, String commodity, String centre, Instant fetchedAt) {
        LocalDate asOf = NcdexDates.asOf(Jsoup.parse(html).text()).toLocalDate();
        List<PriceSnapshot> result = new ArrayList<>();
        for (FuturesQuote q : parseQuotes(html, symbol)) {
            BigDecimal price = q.ltp() != null && q.ltp().signum() > 0 ? q.ltp() : q.previousClose();
            if (price == null) {
                continue;
            }
            result.add(PriceSnapshot.futures(commodity, NcdexPriceSource.ID, centre,
                    q.expiry().format(CONTRACT_MONTH), price, asOf, fetchedAt).withRange(q.low(), q.high()));
        }
        return result;
    }

    private static String text(Element row, String css) {
        Element e = row.selectFirst(css);
        return e == null ? null : e.ownText();
    }
}
