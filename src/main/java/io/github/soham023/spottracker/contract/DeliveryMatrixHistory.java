package io.github.soham023.spottracker.contract;

import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Every premium/discount matrix NCDEX has published, by the date it was published ({@code asOfDate}).
 *
 * <p>NCDEX revises the matrix (a centre's discount changes, a quality is added for a new expiry), so a spread
 * on a given day must be adjusted with the matrix in force that day, not with today's. {@link #on(LocalDate)}
 * returns the latest matrix published on or before the date. Days before the first matrix ever scraped get
 * the earliest one stored: it is the closest known and the alternative is no adjustment at all.</p>
 */
public final class DeliveryMatrixHistory {

    private static final DeliveryMatrixHistory EMPTY = new DeliveryMatrixHistory(new TreeMap<>());

    private final NavigableMap<LocalDate, DeliveryMatrix> byAsOf;

    private DeliveryMatrixHistory(NavigableMap<LocalDate, DeliveryMatrix> byAsOf) {
        this.byAsOf = byAsOf;
    }

    /** @param byAsOf matrix per publication date; empty matrices are ignored */
    public static DeliveryMatrixHistory of(Map<LocalDate, DeliveryMatrix> byAsOf) {
        TreeMap<LocalDate, DeliveryMatrix> copy = new TreeMap<>();
        byAsOf.forEach((date, matrix) -> {
            if (date != null && matrix != null && !matrix.isEmpty()) {
                copy.put(date, matrix);
            }
        });
        return copy.isEmpty() ? EMPTY : new DeliveryMatrixHistory(copy);
    }

    /** A single matrix applied to every date (what callers without history get). */
    public static DeliveryMatrixHistory constant(DeliveryMatrix matrix) {
        return matrix == null || matrix.isEmpty() ? EMPTY : of(Map.of(LocalDate.MIN, matrix));
    }

    public static DeliveryMatrixHistory empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return byAsOf.isEmpty();
    }

    /** The matrix in force on {@code date}: latest published on or before it, else the earliest stored. */
    public DeliveryMatrix on(LocalDate date) {
        if (byAsOf.isEmpty()) {
            return DeliveryMatrix.empty();
        }
        Map.Entry<LocalDate, DeliveryMatrix> e = date == null ? null : byAsOf.floorEntry(date);
        return (e != null ? e : byAsOf.firstEntry()).getValue();
    }
}
