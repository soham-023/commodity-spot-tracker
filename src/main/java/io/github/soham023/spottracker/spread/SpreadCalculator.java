package io.github.soham023.spottracker.spread;

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
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Pure spread maths over a list of snapshots for one commodity (no Spring, no database).
 *
 * <ul>
 *   <li><b>Basis</b>: near-month futures (earliest contract quoted that day) − NCDEX benchmark spot.</li>
 *   <li><b>Mandi spread</b>: average modal price across varieties at a mandi − NCDEX benchmark spot.</li>
 * </ul>
 * NCDEX spot polls can lag several days, so each day uses the latest spot poll on or before it,
 * within {@link SpreadProperties#maxSpotAgeDays()}.
 */
public class SpreadCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SpreadProperties props;

    public SpreadCalculator(SpreadProperties props) {
        this.props = props;
    }

    public List<SpreadPoint> compute(String commodity, List<PriceSnapshot> snapshots) {
        TreeMap<LocalDate, BigDecimal> spot = new TreeMap<>();
        Map<LocalDate, PriceSnapshot> nearMonth = new TreeMap<>();
        Map<String, TreeMap<LocalDate, List<BigDecimal>>> mandi = new TreeMap<>();

        for (PriceSnapshot s : snapshots) {
            if (!s.getCommodity().equalsIgnoreCase(commodity)) {
                continue;
            }
            boolean ncdex = NcdexPriceSource.ID.equals(s.getSource());
            if (ncdex && s.getPriceType() == PriceType.SPOT
                    && s.getMarket().equalsIgnoreCase(props.benchmarkMarket())
                    && s.getVariety().equalsIgnoreCase(props.benchmarkVariety())) {
                spot.put(s.getPriceDate(), s.getPrice());
            } else if (ncdex && s.getPriceType() == PriceType.FUTURES) {
                nearMonth.merge(s.getPriceDate(), s,
                        (a, b) -> a.getContractMonth().compareTo(b.getContractMonth()) <= 0 ? a : b);
            } else if (DataGovMandiSource.ID.equals(s.getSource()) && s.getPriceType() == PriceType.SPOT) {
                mandi.computeIfAbsent(s.getMarket(), k -> new TreeMap<>())
                        .computeIfAbsent(s.getPriceDate(), k -> new ArrayList<>())
                        .add(s.getPrice());
            }
        }

        List<SpreadPoint> points = new ArrayList<>();
        Map<String, BigDecimal> previous = new HashMap<>();
        for (PriceSnapshot f : nearMonth.values()) {
            String key = "BASIS";
            add(points, commodity, f.getPriceDate(), SpreadKind.BASIS, f.getContractMonth(), f.getPrice(), spot, previous, key);
        }
        mandi.forEach((market, byDate) -> byDate.forEach((date, modals) -> {
            BigDecimal avg = modals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(modals.size()), 2, RoundingMode.HALF_UP);
            add(points, commodity, date, SpreadKind.MANDI, market, avg, spot, previous, "MANDI:" + market);
        }));
        points.sort(Comparator.comparing(SpreadPoint::date).thenComparing(SpreadPoint::kind).thenComparing(SpreadPoint::label));
        return points;
    }

    private void add(List<SpreadPoint> out, String commodity, LocalDate date, SpreadKind kind, String label,
                     BigDecimal compared, TreeMap<LocalDate, BigDecimal> spot, Map<String, BigDecimal> previous,
                     String seriesKey) {
        BigDecimal prior = previous.put(seriesKey, compared);
        Optional<Map.Entry<LocalDate, BigDecimal>> ref = Optional.ofNullable(spot.floorEntry(date))
                .filter(e -> ChronoUnit.DAYS.between(e.getKey(), date) <= props.maxSpotAgeDays());
        if (ref.isEmpty()) {
            return;
        }
        BigDecimal spotPrice = ref.get().getValue();
        BigDecimal spread = compared.subtract(spotPrice);
        BigDecimal spreadPct = pct(spread, spotPrice);
        BigDecimal movePct = prior == null || prior.signum() == 0 ? null : pct(compared.subtract(prior), prior);

        EnumSet<SpreadFlag> flags = EnumSet.noneOf(SpreadFlag.class);
        if (spreadPct.abs().compareTo(props.thresholdPct()) > 0) {
            flags.add(SpreadFlag.WIDE_SPREAD);
        }
        if (movePct != null && movePct.abs().compareTo(props.dayMovePct()) > 0) {
            flags.add(SpreadFlag.SHARP_MOVE);
        }
        out.add(new SpreadPoint(commodity.toUpperCase(), date, kind, label, spotPrice, ref.get().getKey(), compared,
                spread, spreadPct, movePct, flags.isEmpty() ? java.util.Set.of() : java.util.Set.copyOf(flags)));
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
