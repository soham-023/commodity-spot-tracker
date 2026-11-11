package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.source.Prices;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the NCDEX product page ({@code /products/TMCFGRNZM}). The margin and premium/discount tables
 * are server-rendered; the margin table is read by header name so a reordered column does not shift values.
 */
public class NcdexProductParser {

    private static final Pattern LTP = Pattern.compile("LTP\\s+([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern PERCENT = Pattern.compile("([+\\-−])?\\s*(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern AT = Pattern.compile("^(.*?)\\s+at\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    public NcdexProductPage parse(String html, String symbol) {
        Document doc = Jsoup.parse(html);
        List<NcdexProductPage.Margin> margins = margins(doc);
        List<NcdexProductPage.PremiumDiscount> matrix = premiumDiscounts(doc, symbol);

        TreeSet<LocalDate> expiries = new TreeSet<>();
        for (Element option : doc.select("select#exp_date option[value], select#premdisc_expdate option[value]")) {
            if (!option.attr("value").isBlank()) {
                expiries.add(NcdexDates.anyDate(option.attr("value")));
            }
        }
        margins.forEach(m -> expiries.add(m.expiry()));
        matrix.forEach(p -> expiries.add(p.expiry()));
        if (expiries.isEmpty()) {
            throw new IllegalArgumentException("No contract expiries found on the NCDEX product page for " + symbol);
        }

        BigDecimal ltp = null;
        BigDecimal change = null;
        Element header = doc.selectFirst("div.product-value");
        if (header != null) {
            Matcher m = LTP.matcher(header.ownText());
            ltp = m.find() ? Prices.parse(m.group(1)) : null;
            Element ch = header.selectFirst("span.plus, span.minus");
            if (ch != null) {
                BigDecimal v = Prices.parse(ch.ownText().replace(" ", "").replace("+", ""));
                change = v == null ? null : ch.hasClass("minus") && v.signum() > 0 ? v.negate() : v;
            }
        }
        return new NcdexProductPage(symbol, ltp, change, List.copyOf(expiries), margins, matrix);
    }

    private static List<NcdexProductPage.Margin> margins(Document doc) {
        Element table = doc.selectFirst("table#example2");
        if (table == null) {
            return List.of();
        }
        Map<String, Integer> col = new HashMap<>();
        Elements headers = table.select("thead th");
        for (int i = 0; i < headers.size(); i++) {
            col.put(headers.get(i).text().trim().toUpperCase(Locale.ROOT), i);
        }
        for (String required : List.of("MARGIN DATE", "EXPIRY", "TM")) {
            if (!col.containsKey(required)) {
                throw new IllegalArgumentException("Margin table has no '" + required + "' column; page layout changed?");
            }
        }
        List<NcdexProductPage.Margin> out = new ArrayList<>();
        for (Element row : table.select("tbody > tr")) {
            Elements cells = row.select("> td");
            if (cells.size() < headers.size()) {
                continue;
            }
            BigDecimal total = pct(cells, col, "TM");
            if (total == null) {
                continue;
            }
            out.add(new NcdexProductPage.Margin(
                    text(cells, col, "FILE TYPE"),
                    NcdexDates.anyDate(text(cells, col, "MARGIN DATE")),
                    NcdexDates.anyDate(text(cells, col, "EXPIRY")),
                    pct(cells, col, "IM"), pct(cells, col, "EM"), pct(cells, col, "ADD. PEM"), total,
                    pct(cells, col, "ADD. LONG"), pct(cells, col, "ADD. SHORT"),
                    pct(cells, col, "SPCML"), pct(cells, col, "SPCMS")));
        }
        return out;
    }

    private static List<NcdexProductPage.PremiumDiscount> premiumDiscounts(Document doc, String symbol) {
        List<NcdexProductPage.PremiumDiscount> out = new ArrayList<>();
        for (Element row : doc.select("table#example3 tbody > tr")) {
            Elements c = row.select("> td");
            if (c.size() < 5 || !c.get(0).text().trim().equalsIgnoreCase(symbol)) {
                continue;
            }
            String location = c.get(3).text().trim();
            String value = c.get(4).text().trim();
            boolean basis = value.equalsIgnoreCase("Basis");
            BigDecimal premium = basis ? BigDecimal.ZERO.setScale(2) : premium(value);
            Matcher at = AT.matcher(location);
            String quality = at.matches() ? at.group(1).replaceAll("(?i)\\s+quality$", "").trim() : location;
            String centre = at.matches() ? at.group(2).trim() : c.get(2).text().trim();
            out.add(new NcdexProductPage.PremiumDiscount(NcdexDates.anyDate(c.get(1).text()), c.get(2).text().trim(),
                    location, quality, centre, premium, basis));
        }
        return out;
    }

    /** "+ 4%", "- 4%", "+4%", "No Premium/Discount". */
    static BigDecimal premium(String value) {
        if (value.toLowerCase(Locale.ROOT).startsWith("no premium")) {
            return BigDecimal.ZERO.setScale(2);
        }
        Matcher m = PERCENT.matcher(value);
        if (!m.find()) {
            throw new IllegalArgumentException("Unrecognised premium/discount '" + value + "'");
        }
        BigDecimal v = new BigDecimal(m.group(2)).setScale(2);
        return m.group(1) != null && !m.group(1).equals("+") ? v.negate() : v;
    }

    private static String text(Elements cells, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null ? "" : cells.get(i).text().trim();
    }

    private static BigDecimal pct(Elements cells, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null ? null : Prices.parse(cells.get(i).text());
    }
}
