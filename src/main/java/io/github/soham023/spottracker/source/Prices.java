package io.github.soham023.spottracker.source;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Parsing helpers for the number formats Indian market sites publish (e.g. {@code "28,648.00"}). */
public final class Prices {

    private Prices() {
    }

    public static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace(",", "").replace("₹", "").replace("\u00a0", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals("--")) {
            return null;
        }
        return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
    }
}
