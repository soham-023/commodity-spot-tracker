package io.github.soham023.spottracker.source.ncdex;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
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
}
