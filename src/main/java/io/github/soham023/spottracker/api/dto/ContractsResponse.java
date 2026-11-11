package io.github.soham023.spottracker.api.dto;

import io.github.soham023.spottracker.contract.DailyMargin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * NCDEX contract calendar with the latest daily margins (all margins in % of contract value).
 *
 * @param nearMonth  expiry the basis is measured against today (first expiry outside the roll window)
 * @param marginDate latest margin date stored
 */
public record ContractsResponse(String symbol, String productUrl, LocalDate today, int rollDays, LocalDate nearMonth,
                                LocalDate marginDate, List<Contract> contracts) {

    /**
     * @param active            still listed on the product page at the last scrape
     * @param totalMarginChange TM change vs the previous margin date, percentage points (null without history)
     */
    public record Contract(LocalDate expiry, String contractMonth, long daysToExpiry, boolean nearMonth,
                           boolean inRollWindow, boolean active, Margin margin, BigDecimal totalMarginChange) {
    }

    public record Margin(LocalDate marginDate, String fileType, BigDecimal initial, BigDecimal extremeLoss,
                         BigDecimal additionalPem, BigDecimal total, BigDecimal additionalLong,
                         BigDecimal additionalShort, BigDecimal specialLong, BigDecimal specialShort) {

        public static Margin from(DailyMargin m) {
            return new Margin(m.getMarginDate(), m.getFileType(), m.getInitialMargin(), m.getExtremeMargin(),
                    m.getAdditionalPem(), m.getTotalMargin(), m.getAdditionalLong(), m.getAdditionalShort(),
                    m.getSpecialLong(), m.getSpecialShort());
        }
    }
}
