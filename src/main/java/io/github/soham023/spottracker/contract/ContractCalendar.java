package io.github.soham023.spottracker.contract;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Futures expiries for one symbol plus the roll rule: a contract stops being the near month once it is
 * within {@code rollDays} calendar days of expiry (from then on the next contract is used). Contracts
 * the calendar does not know (e.g. history from before the product page was first scraped) are treated
 * as tradable, so an empty calendar falls back to "earliest contract quoted that day".
 */
public final class ContractCalendar {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TreeMap<String, LocalDate> byMonth = new TreeMap<>();
    private final int rollDays;

    public ContractCalendar(Collection<LocalDate> expiries, int rollDays) {
        if (rollDays < 0) {
            throw new IllegalArgumentException("rollDays must not be negative");
        }
        expiries.forEach(e -> byMonth.put(e.format(MONTH), e));
        this.rollDays = rollDays;
    }

    public static ContractCalendar empty() {
        return new ContractCalendar(List.of(), 0);
    }

    public boolean isEmpty() {
        return byMonth.isEmpty();
    }

    public int rollDays() {
        return rollDays;
    }

    public List<LocalDate> expiries() {
        return List.copyOf(byMonth.values());
    }

    public Optional<LocalDate> expiryOf(String contractMonth) {
        return Optional.ofNullable(byMonth.get(contractMonth));
    }

    /** False once a known contract is within the roll window (or expired); unknown contracts are tradable. */
    public boolean isTradable(String contractMonth, LocalDate on) {
        return expiryOf(contractMonth).map(e -> ChronoUnit.DAYS.between(on, e) > rollDays).orElse(true);
    }

    /** True when a known contract is inside its roll window but not yet expired. */
    public boolean inRollWindow(LocalDate expiry, LocalDate on) {
        long days = ChronoUnit.DAYS.between(on, expiry);
        return days >= 0 && days <= rollDays;
    }

    /** The near-month expiry on a date by the calendar alone (first expiry outside the roll window). */
    public Optional<LocalDate> nearMonth(LocalDate on) {
        return byMonth.values().stream().filter(e -> ChronoUnit.DAYS.between(on, e) > rollDays).findFirst();
    }

    /** Of the contracts quoted on {@code on}, the earliest one that has not entered its roll window. */
    public <T> Optional<T> pickNearMonth(Collection<T> quotes, Function<T, String> contractMonth, LocalDate on) {
        return quotes.stream()
                .filter(q -> isTradable(contractMonth.apply(q), on))
                .min(Comparator.comparing(contractMonth));
    }

    public static String month(LocalDate expiry) {
        return expiry.format(MONTH);
    }
}
