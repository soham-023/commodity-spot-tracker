package io.github.soham023.spottracker.api.dto;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A price in ₹ per quintal. {@code contractMonth} and {@code variety} are null when not applicable. */
public record PriceDto(
        Long id,
        String commodity,
        String source,
        String market,
        String variety,
        PriceType priceType,
        String contractMonth,
        BigDecimal price,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LocalDate date,
        Instant fetchedAt) {

    public static PriceDto from(PriceSnapshot s) {
        return new PriceDto(s.getId(), s.getCommodity(), s.getSource(), s.getMarket(), emptyToNull(s.getVariety()),
                s.getPriceType(), emptyToNull(s.getContractMonth()), s.getPrice(), s.getMinPrice(), s.getMaxPrice(),
                s.getPriceDate(), s.getFetchedAt());
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
