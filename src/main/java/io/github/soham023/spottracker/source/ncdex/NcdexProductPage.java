package io.github.soham023.spottracker.source.ncdex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the NCDEX product page ({@code /products/<symbol>}) publishes about one futures contract.
 *
 * @param symbol           futures symbol, e.g. TMCFGRNZM
 * @param lastTradedPrice  "LTP" in the page header (contract not stated by NCDEX; informational only)
 * @param change           change next to the LTP
 * @param expiries         every active expiry the page lists, ascending
 * @param margins          "Daily margin information", one row per expiry
 * @param premiumDiscounts "Premium Discount Matrix", one row per expiry and delivery location
 */
public record NcdexProductPage(
        String symbol,
        BigDecimal lastTradedPrice,
        BigDecimal change,
        List<LocalDate> expiries,
        List<Margin> margins,
        List<PremiumDiscount> premiumDiscounts) {

    /**
     * One row of the daily margin table. Every value is a % of contract value.
     *
     * @param fileType        NCCL margin file the row comes from, e.g. "Morning"
     * @param initialMargin   IM
     * @param extremeMargin   EM (extreme loss margin)
     * @param additionalPem   Add. PEM (pre-expiry margin)
     * @param totalMargin     TM
     * @param additionalLong  additional margin on long positions
     * @param additionalShort additional margin on short positions
     * @param specialLong     SPCML (special margin, long)
     * @param specialShort    SPCMS (special margin, short)
     */
    public record Margin(String fileType, LocalDate marginDate, LocalDate expiry, BigDecimal initialMargin,
                         BigDecimal extremeMargin, BigDecimal additionalPem, BigDecimal totalMargin,
                         BigDecimal additionalLong, BigDecimal additionalShort, BigDecimal specialLong,
                         BigDecimal specialShort) {
    }

    /**
     * One delivery location in the premium/discount matrix.
     *
     * @param location    as published, e.g. "Rajapore quality at Sangli"
     * @param quality     the part before " at ", without a trailing "quality" ("Rajapore")
     * @param centre      the delivery centre after " at " ("Sangli")
     * @param premiumPct  premium (+) or discount (−) in % over the basis quality; 0 for the basis itself
     * @param basis       true for the row NCDEX marks as "Basis"
     */
    public record PremiumDiscount(LocalDate expiry, String basisLocation, String location, String quality,
                                  String centre, BigDecimal premiumPct, boolean basis) {
    }
}
