package io.github.soham023.spottracker.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Day-over-day change in total margin (TM) for one expiry.
 *
 * @param change totalMargin − previousTotalMargin, percentage points of contract value
 */
public record MarginChange(String symbol, LocalDate expiry, LocalDate marginDate, String fileType,
                           BigDecimal totalMargin, LocalDate previousDate, BigDecimal previousTotalMargin,
                           BigDecimal change, BigDecimal additionalLong, BigDecimal additionalShort) {

    /**
     * Compares the two most recent margin dates for {@code expiry}. Within a date the most recently fetched
     * file wins. Empty when fewer than two dates are known.
     */
    public static Optional<MarginChange> latest(List<DailyMargin> margins, LocalDate expiry) {
        Map<LocalDate, DailyMargin> byDate = new LinkedHashMap<>();
        margins.stream()
                .filter(m -> m.getExpiryDate().equals(expiry))
                .sorted(Comparator.comparing(DailyMargin::getMarginDate).reversed()
                        .thenComparing(DailyMargin::getFetchedAt, Comparator.reverseOrder()))
                .forEach(m -> byDate.putIfAbsent(m.getMarginDate(), m));
        if (byDate.size() < 2) {
            return Optional.empty();
        }
        var it = byDate.values().iterator();
        DailyMargin now = it.next();
        DailyMargin before = it.next();
        return Optional.of(new MarginChange(now.getSymbol(), expiry, now.getMarginDate(), now.getFileType(),
                now.getTotalMargin(), before.getMarginDate(), before.getTotalMargin(),
                now.getTotalMargin().subtract(before.getTotalMargin()), now.getAdditionalLong(), now.getAdditionalShort()));
    }

    public boolean exceeds(BigDecimal points) {
        return change.abs().compareTo(points) > 0;
    }
}
