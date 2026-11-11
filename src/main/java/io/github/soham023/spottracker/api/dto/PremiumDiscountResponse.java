package io.github.soham023.spottracker.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The NCDEX premium/discount matrix as last published.
 *
 * @param basis  the basis quality and location every premium is relative to
 */
public record PremiumDiscountResponse(String symbol, LocalDate asOf, String basis, String productUrl, List<Row> rows) {

    /**
     * @param premiumPct  + premium / − discount over the basis quality, %
     * @param divisor     what a spot quote of this grade is divided by to get its basis-quality equivalent
     */
    public record Row(LocalDate expiry, String location, String quality, String centre, BigDecimal premiumPct,
                      boolean basis, BigDecimal divisor) {
    }
}
