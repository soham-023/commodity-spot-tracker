package io.github.soham023.spottracker.source.ncdex;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date formats used across ncdex.com pages. */
final class NcdexDates {

    /** "As on Wednesday, Nov 11 2026 12:45 PM" */
    private static final Pattern AS_ON = Pattern.compile("As on\\s+\\w+,\\s+(\\w{3} \\d{1,2} \\d{4} \\d{1,2}:\\d{2} [AP]M)");
    private static final DateTimeFormatter AS_ON_FMT = ci("MMM d yyyy hh:mm a");
    /** "Nov 04 2026 | 03:33 PM" */
    private static final DateTimeFormatter POLL_FMT = ci("MMM dd yyyy | hh:mm a");
    /** "18-Dec-2026" */
    static final DateTimeFormatter EXPIRY_FMT = ci("dd-MMM-yyyy");
    /** "11-Nov-26" (product page margin and premium/discount tables) */
    private static final DateTimeFormatter SHORT_FMT = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("dd-MMM-").appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.ENGLISH);

    private NcdexDates() {
    }

    private static DateTimeFormatter ci(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH);
    }

    static LocalDateTime asOf(String text) {
        Matcher m = AS_ON.matcher(text);
        if (!m.find()) {
            throw new IllegalArgumentException("No 'As on' timestamp found");
        }
        return LocalDateTime.parse(m.group(1).replaceAll("\\s+", " "), AS_ON_FMT);
    }

    static LocalDateTime pollingTime(String text) {
        return LocalDateTime.parse(text.trim().replaceAll("\\s+", " "), POLL_FMT);
    }

    static LocalDate expiry(String text) {
        return LocalDate.parse(text.trim(), EXPIRY_FMT);
    }

    /** Accepts every date shape the product page mixes: "2026-12-18", "18-Dec-2026" and "18-Dec-26". */
    static LocalDate anyDate(String text) {
        String t = text.trim();
        if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(t);
        }
        if (t.matches("\\d{1,2}-[A-Za-z]{3}-\\d{4}")) {
            return LocalDate.parse(t, EXPIRY_FMT);
        }
        return LocalDate.parse(t, SHORT_FMT);
    }
}
