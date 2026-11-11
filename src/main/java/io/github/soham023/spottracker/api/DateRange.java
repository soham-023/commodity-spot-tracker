package io.github.soham023.spottracker.api;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Validated [from, to] query window with sensible defaults. */
record DateRange(LocalDate from, LocalDate to) {

    static final int MAX_DAYS = 3660;

    static DateRange of(LocalDate from, LocalDate to, LocalDate today, int defaultDays) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(defaultDays) : from;
        if (start.isAfter(end)) {
            throw new InvalidRequestException("'from' (" + start + ") must not be after 'to' (" + end + ")");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw new InvalidRequestException("Date range must not exceed " + MAX_DAYS + " days");
        }
        return new DateRange(start, end);
    }
}
