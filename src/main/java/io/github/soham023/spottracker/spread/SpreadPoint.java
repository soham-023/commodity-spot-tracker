package io.github.soham023.spottracker.spread;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * One spread observation.
 *
 * @param label             contract month for {@link SpreadKind#BASIS}, mandi name for {@link SpreadKind#MANDI},
 *                          "quality @ centre" for {@link SpreadKind#DELIVERY}
 * @param spotDate          date of the NCDEX spot poll used as reference (may be earlier than {@code date})
 * @param spread            raw spread: comparedPrice − spotPrice, ₹/quintal
 * @param spreadPct         raw spread as % of spotPrice
 * @param priceMovePct      % change of comparedPrice vs. its previous observation, null on the first one
 * @param adjustmentPct     NCDEX premium (+) / discount (−) applied to the non-basis side, null when the
 *                          premium/discount matrix has no entry for it (then no adjusted values either)
 * @param adjustedSpread    spread after converting the delivery grade to basis-quality equivalent
 *                          (price ÷ (1 + adjustmentPct/100)), ₹/quintal
 * @param adjustedSpreadPct adjustedSpread as % of the basis-equivalent spot. WIDE_SPREAD uses this when present.
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
        Set<SpreadFlag> flags,
        BigDecimal adjustmentPct,
        BigDecimal adjustedSpread,
        BigDecimal adjustedSpreadPct) {

    /** A point without a delivery adjustment. */
    public SpreadPoint(String commodity, LocalDate date, SpreadKind kind, String label, BigDecimal spotPrice,
                       LocalDate spotDate, BigDecimal comparedPrice, BigDecimal spread, BigDecimal spreadPct,
                       BigDecimal priceMovePct, Set<SpreadFlag> flags) {
        this(commodity, date, kind, label, spotPrice, spotDate, comparedPrice, spread, spreadPct, priceMovePct, flags,
                null, null, null);
    }

    public boolean flagged() {
        return !flags.isEmpty();
    }

    /** The spread % alerting looks at: delivery-adjusted when known, raw otherwise. */
    public BigDecimal effectiveSpreadPct() {
        return adjustedSpreadPct != null ? adjustedSpreadPct : spreadPct;
    }
}
