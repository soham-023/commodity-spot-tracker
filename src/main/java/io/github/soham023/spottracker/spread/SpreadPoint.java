package io.github.soham023.spottracker.spread;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * One spread observation.
 *
 * @param label        contract month for {@link SpreadKind#BASIS}, mandi name for {@link SpreadKind#MANDI}
 * @param spotDate     date of the NCDEX spot poll used as reference (may be earlier than {@code date})
 * @param spread       comparedPrice − spotPrice, ₹/quintal
 * @param spreadPct    spread as % of spotPrice
 * @param priceMovePct % change of comparedPrice vs. its previous observation, null on the first one
 */
public record SpreadPoint(
        String commodity,
        LocalDate date,
        SpreadKind kind,
        String label,
        BigDecimal spotPrice,
        LocalDate spotDate,
        BigDecimal comparedPrice,
        BigDecimal spread,
        BigDecimal spreadPct,
        BigDecimal priceMovePct,
        Set<SpreadFlag> flags) {

    public boolean flagged() {
        return !flags.isEmpty();
    }
}
