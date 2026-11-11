package io.github.soham023.spottracker.domain;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Composable filters for the price history endpoint. A null argument means "no filter". */
public final class PriceSnapshotSpecs {

    private PriceSnapshotSpecs() {
    }

    public static Specification<PriceSnapshot> filter(String commodity, String source, String market,
                                                      PriceType type, LocalDate from, LocalDate to) {
        return Specification.where(eqIgnoreCase("commodity", commodity))
                .and(eqIgnoreCase("source", source))
                .and(eqIgnoreCase("market", market))
                .and(type == null ? null : (root, q, cb) -> cb.equal(root.get("priceType"), type))
                .and(from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("priceDate"), from))
                .and(to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("priceDate"), to));
    }

    private static Specification<PriceSnapshot> eqIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(cb.upper(root.get(field)), value.trim().toUpperCase());
    }
}
