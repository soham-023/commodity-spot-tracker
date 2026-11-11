package io.github.soham023.spottracker.contract;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The NCDEX premium/discount matrix as a lookup: what a quality at a delivery centre is worth relative
 * to the contract's basis quality (Farmer polished fingers, Nizamabad, for TMCFGRNZM).
 *
 * <p>A spot quote for a delivery grade is converted to its basis-quality equivalent by dividing by
 * {@code 1 + premium/100}: Rajapore at Sangli (+4%) ₹28,650 → ₹27,548; Unpolished at Nizamabad (−4%)
 * ₹23,698.55 → ₹24,686. Comparing that equivalent with futures removes the part of the spread the
 * exchange already prices in for variety and location.</p>
 */
public final class DeliveryMatrix {

    /** One matrix row reduced to what the lookup needs. */
    public record Entry(LocalDate expiry, String quality, String centre, BigDecimal premiumPct, boolean basis) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TreeMap<LocalDate, List<Entry>> byExpiry;

    public DeliveryMatrix(Collection<Entry> entries) {
        this.byExpiry = entries.stream().collect(Collectors.groupingBy(Entry::expiry, TreeMap::new, Collectors.toList()));
    }

    public static DeliveryMatrix empty() {
        return new DeliveryMatrix(List.of());
    }

    public boolean isEmpty() {
        return byExpiry.isEmpty();
    }

    public Map<LocalDate, List<Entry>> entries() {
        return byExpiry;
    }

    /**
     * Premium for a quote whose variety is known exactly (an NCDEX polled spot such as "Rajapore" at Sangli).
     * Empty unless exactly one quality at that centre matches the variety.
     */
    public Optional<Entry> forGrade(LocalDate expiry, String centre, String variety) {
        List<Entry> atCentre = atCentre(expiry, centre);
        List<Entry> matches = atCentre.stream().filter(e -> matches(e.quality(), variety)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    /**
     * Premium for a mandi average, whose variety names (Agmarknet "Finger", "Rajapuri", "Other") rarely match
     * the exchange's: the variety is tried first, then a centre with a single deliverable quality is used.
     * Centres with several qualities and no variety match (Erode, Nizamabad) stay unadjusted.
     */
    public Optional<Entry> forMandi(LocalDate expiry, String centre, String variety) {
        Optional<Entry> byVariety = forGrade(expiry, centre, variety);
        if (byVariety.isPresent()) {
            return byVariety;
        }
        List<Entry> atCentre = atCentre(expiry, centre);
        return atCentre.size() == 1 ? Optional.of(atCentre.getFirst()) : Optional.empty();
    }

    /** {@code price / (1 + premium/100)}, the basis-quality equivalent of a quote at a premium or discount. */
    public static BigDecimal toBasisEquivalent(BigDecimal price, BigDecimal premiumPct) {
        BigDecimal factor = BigDecimal.ONE.add(premiumPct.divide(HUNDRED, MathContext.DECIMAL64));
        return price.divide(factor, 2, RoundingMode.HALF_UP);
    }

    /** Entries for {@code expiry}; if the matrix has no rows for it, those of the nearest later (else earliest) expiry. */
    private List<Entry> atCentre(LocalDate expiry, String centre) {
        if (byExpiry.isEmpty() || centre == null) {
            return List.of();
        }
        var e = expiry == null ? null : byExpiry.ceilingEntry(expiry);
        List<Entry> rows = e != null ? e.getValue() : byExpiry.firstEntry().getValue();
        String c = norm(centre);
        return rows.stream().filter(r -> c.startsWith(norm(r.centre()))).toList();
    }

    private static boolean matches(String quality, String variety) {
        if (variety == null || variety.isBlank()) {
            return false;
        }
        return (" " + norm(quality) + " ").contains(" " + norm(variety));
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("rajapuri", "rajapore").replaceAll("\\s+", " ").trim();
    }
}
