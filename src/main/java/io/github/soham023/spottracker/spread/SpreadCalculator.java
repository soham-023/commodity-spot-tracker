package io.github.soham023.spottracker.spread;

import io.github.soham023.spottracker.contract.ContractCalendar;
import io.github.soham023.spottracker.contract.DeliveryMatrix;
import io.github.soham023.spottracker.contract.DeliveryMatrixHistory;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.source.agmarknet.DataGovMandiSource;
import io.github.soham023.spottracker.source.ncdex.NcdexPriceSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Pure spread maths over a list of snapshots for one commodity (no Spring, no database).
 *
 * <ul>
 *   <li><b>Basis</b>: near-month futures − NCDEX benchmark spot. The near month is the earliest contract
 *   quoted that day that is not inside its roll window per the scraped {@link ContractCalendar}.</li>
 *   <li><b>Delivery</b>: near-month futures − another NCDEX-polled deliverable grade (Unpolished at Nizamabad,
 *   Rajapore at Sangli), only for grades the premium/discount matrix prices.</li>
 *   <li><b>Mandi spread</b>: average modal price across varieties at a mandi − NCDEX benchmark spot.</li>
 * </ul>
 * Every point keeps its raw spread; when the {@link DeliveryMatrix} in force on that day (see
 * {@link DeliveryMatrixHistory}) prices the grade or centre, the point also
 * carries a delivery-adjusted spread (grade converted to basis-quality equivalent), and the WIDE_SPREAD flag
 * is judged on that, so a Rajapore premium or an unpolished discount the exchange already allows for does
 * not raise an alert. NCDEX spot polls can lag several days, so each day uses the latest spot poll on or
 * before it, within {@link SpreadProperties#maxSpotAgeDays()}.
 */
public class SpreadCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SpreadProperties props;

    public SpreadCalculator(SpreadProperties props) {
        this.props = props;
    }

    public List<SpreadPoint> compute(String commodity, List<PriceSnapshot> snapshots) {
        return compute(commodity, snapshots, ContractCalendar.empty(), DeliveryMatrix.empty());
    }

    public List<SpreadPoint> compute(String commodity, List<PriceSnapshot> snapshots, ContractCalendar calendar) {
        return compute(commodity, snapshots, calendar, DeliveryMatrix.empty());
    }

    /** Applies one matrix to every date. */
    public List<SpreadPoint> compute(String commodity, List<PriceSnapshot> snapshots, ContractCalendar calendar,
                                     DeliveryMatrix matrix) {
        return compute(commodity, snapshots, calendar, DeliveryMatrixHistory.constant(matrix));
    }

    /** Adjusts each date with the premium/discount matrix NCDEX had published by that date. */
    public List<SpreadPoint> compute(String commodity, List<PriceSnapshot> snapshots, ContractCalendar calendar,
                                     DeliveryMatrixHistory matrices) {
        TreeMap<LocalDate, BigDecimal> spot = new TreeMap<>();
        Map<Grade, TreeMap<LocalDate, BigDecimal>> grades = new TreeMap<>();
        Map<LocalDate, List<PriceSnapshot>> futuresByDate = new TreeMap<>();
        Map<String, TreeMap<LocalDate, List<PriceSnapshot>>> mandi = new TreeMap<>();

        for (PriceSnapshot s : snapshots) {
            if (!s.getCommodity().equalsIgnoreCase(commodity)) {
                continue;
            }
            boolean ncdex = NcdexPriceSource.ID.equals(s.getSource());
            if (ncdex && s.getPriceType() == PriceType.SPOT) {
                if (s.getMarket().equalsIgnoreCase(props.benchmarkMarket())
                        && s.getVariety().equalsIgnoreCase(props.benchmarkVariety())) {
                    spot.put(s.getPriceDate(), s.getPrice());
                } else {
                    grades.computeIfAbsent(new Grade(s.getVariety(), s.getMarket()), k -> new TreeMap<>())
                            .put(s.getPriceDate(), s.getPrice());
                }
            } else if (ncdex && s.getPriceType() == PriceType.FUTURES) {
                futuresByDate.computeIfAbsent(s.getPriceDate(), k -> new ArrayList<>()).add(s);
            } else if (DataGovMandiSource.ID.equals(s.getSource()) && s.getPriceType() == PriceType.SPOT) {
                mandi.computeIfAbsent(s.getMarket(), k -> new TreeMap<>())
                        .computeIfAbsent(s.getPriceDate(), k -> new ArrayList<>())
                        .add(s);
            }
        }

        List<SpreadPoint> points = new ArrayList<>();
        Map<String, BigDecimal> previous = new HashMap<>();
        futuresByDate.forEach((date, quotes) -> calendar.pickNearMonth(quotes, PriceSnapshot::getContractMonth, date)
                .ifPresent(f -> {
                    DeliveryMatrix matrix = matrices.on(date);
                    LocalDate expiry = calendar.expiryOf(f.getContractMonth()).orElse(null);
                    BigDecimal basisPremium = matrix.forGrade(expiry, props.benchmarkMarket(), props.benchmarkVariety())
                            .map(DeliveryMatrix.Entry::premiumPct).orElse(null);
                    futuresVsSpot(points, commodity, date, SpreadKind.BASIS, f.getContractMonth(), f.getPrice(),
                            spot, basisPremium, previous, "BASIS");
                    grades.forEach((grade, series) -> matrix.forGrade(expiry, grade.centre(), grade.variety())
                            .ifPresent(e -> futuresVsSpot(points, commodity, date, SpreadKind.DELIVERY,
                                    grade.variety() + " @ " + grade.centre(), f.getPrice(), series, e.premiumPct(),
                                    previous, null)));
                }));
        mandi.forEach((market, byDate) -> byDate.forEach((date, rows) -> {
            BigDecimal avg = rows.stream().map(PriceSnapshot::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
            DeliveryMatrix matrix = matrices.on(date);
            LocalDate expiry = calendar.nearMonth(date).orElse(null);
            Set<String> varieties = rows.stream().map(PriceSnapshot::getVariety).collect(Collectors.toSet());
            BigDecimal premium = varieties.size() == 1
                    ? matrix.forMandi(expiry, market, varieties.iterator().next()).map(DeliveryMatrix.Entry::premiumPct)
                    .orElse(null)
                    : matrix.forMandi(expiry, market, null).map(DeliveryMatrix.Entry::premiumPct).orElse(null);
            mandiVsSpot(points, commodity, date, market, avg, spot, premium, previous);
        }));
        points.sort(Comparator.comparing(SpreadPoint::date).thenComparing(SpreadPoint::kind).thenComparing(SpreadPoint::label));
        return points;
    }

    private record Grade(String variety, String centre) implements Comparable<Grade> {
        @Override
        public int compareTo(Grade o) {
            return Comparator.comparing(Grade::centre).thenComparing(Grade::variety).compare(this, o);
        }
    }

    /** Futures vs a spot grade; the adjustment converts the spot grade to its basis-quality equivalent. */
    private void futuresVsSpot(List<SpreadPoint> out, String commodity, LocalDate date, SpreadKind kind, String label,
                               BigDecimal futures, TreeMap<LocalDate, BigDecimal> spotSeries, BigDecimal premium,
                               Map<String, BigDecimal> previous, String seriesKey) {
        BigDecimal prior = seriesKey == null ? null : previous.put(seriesKey, futures);
        reference(spotSeries, date).ifPresent(ref -> {
            BigDecimal spotPrice = ref.getValue();
            BigDecimal adjustedSpread = null;
            BigDecimal adjustedPct = null;
            if (premium != null) {
                BigDecimal equivalent = DeliveryMatrix.toBasisEquivalent(spotPrice, premium);
                adjustedSpread = futures.subtract(equivalent);
                adjustedPct = pct(adjustedSpread, equivalent);
            }
            out.add(point(commodity, date, kind, label, spotPrice, ref.getKey(), futures, prior, premium,
                    adjustedSpread, adjustedPct));
        });
    }

    /** Mandi modal vs benchmark spot; the adjustment converts the mandi's grade to its basis-quality equivalent. */
    private void mandiVsSpot(List<SpreadPoint> out, String commodity, LocalDate date, String market, BigDecimal modal,
                             TreeMap<LocalDate, BigDecimal> spot, BigDecimal premium, Map<String, BigDecimal> previous) {
        BigDecimal prior = previous.put("MANDI:" + market, modal);
        reference(spot, date).ifPresent(ref -> {
            BigDecimal spotPrice = ref.getValue();
            BigDecimal adjustedSpread = null;
            BigDecimal adjustedPct = null;
            if (premium != null) {
                adjustedSpread = DeliveryMatrix.toBasisEquivalent(modal, premium).subtract(spotPrice);
                adjustedPct = pct(adjustedSpread, spotPrice);
            }
            out.add(point(commodity, date, SpreadKind.MANDI, market, spotPrice, ref.getKey(), modal, prior, premium,
                    adjustedSpread, adjustedPct));
        });
    }

    private Optional<Map.Entry<LocalDate, BigDecimal>> reference(TreeMap<LocalDate, BigDecimal> series, LocalDate date) {
        return Optional.ofNullable(series.floorEntry(date))
                .filter(e -> ChronoUnit.DAYS.between(e.getKey(), date) <= props.maxSpotAgeDays());
    }

    private SpreadPoint point(String commodity, LocalDate date, SpreadKind kind, String label, BigDecimal spotPrice,
                              LocalDate spotDate, BigDecimal compared, BigDecimal prior, BigDecimal premium,
                              BigDecimal adjustedSpread, BigDecimal adjustedPct) {
        BigDecimal spread = compared.subtract(spotPrice);
        BigDecimal spreadPct = pct(spread, spotPrice);
        BigDecimal movePct = prior == null || prior.signum() == 0 ? null : pct(compared.subtract(prior), prior);

        EnumSet<SpreadFlag> flags = EnumSet.noneOf(SpreadFlag.class);
        BigDecimal judged = adjustedPct != null ? adjustedPct : spreadPct;
        if (judged.abs().compareTo(props.thresholdPct()) > 0) {
            flags.add(SpreadFlag.WIDE_SPREAD);
        }
        if (movePct != null && movePct.abs().compareTo(props.dayMovePct()) > 0) {
            flags.add(SpreadFlag.SHARP_MOVE);
        }
        return new SpreadPoint(commodity.toUpperCase(), date, kind, label, spotPrice, spotDate, compared, spread,
                spreadPct, movePct, flags.isEmpty() ? Set.of() : Set.copyOf(flags), premium, adjustedSpread, adjustedPct);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    /** Convenience: only the most recent date in the result. */
    public static List<SpreadPoint> latest(List<SpreadPoint> points) {
        return points.stream().map(SpreadPoint::date).max(Comparator.naturalOrder())
                .map(d -> points.stream().filter(p -> p.date().equals(d)).collect(Collectors.toList()))
                .orElse(List.of());
    }
}
