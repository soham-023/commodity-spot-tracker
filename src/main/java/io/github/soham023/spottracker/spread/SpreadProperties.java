package io.github.soham023.spottracker.spread;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * @param thresholdPct      flag when |spread| exceeds this % of the NCDEX spot price (default 5)
 * @param dayMovePct        flag when the compared price moves more than this % since its previous observation (default 3)
 * @param benchmarkMarket   NCDEX spot centre used as the reference (default Nizamabad)
 * @param benchmarkVariety  NCDEX spot variety used as the reference (default Farmer Polished, the futures basis grade)
 * @param maxSpotAgeDays    how stale an NCDEX spot poll may be and still be used as reference (default 7)
 */
@ConfigurationProperties(prefix = "spot.spread")
public record SpreadProperties(
        BigDecimal thresholdPct,
        BigDecimal dayMovePct,
        String benchmarkMarket,
        String benchmarkVariety,
        Integer maxSpotAgeDays) {

    public SpreadProperties {
        thresholdPct = thresholdPct == null ? new BigDecimal("5") : thresholdPct;
        dayMovePct = dayMovePct == null ? new BigDecimal("3") : dayMovePct;
        benchmarkMarket = benchmarkMarket == null ? "Nizamabad" : benchmarkMarket;
        benchmarkVariety = benchmarkVariety == null ? "Farmer Polished" : benchmarkVariety;
        maxSpotAgeDays = maxSpotAgeDays == null ? 7 : maxSpotAgeDays;
    }

    public static SpreadProperties defaults() {
        return new SpreadProperties(null, null, null, null, null);
    }
}
