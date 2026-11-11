package io.github.soham023.spottracker.spread;

import io.github.soham023.spottracker.domain.PriceSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SpreadService {

    private final PriceSnapshotRepository repository;
    private final SpreadProperties props;
    private final SpreadCalculator calculator;

    public SpreadService(PriceSnapshotRepository repository, SpreadProperties props) {
        this.repository = repository;
        this.props = props;
        this.calculator = new SpreadCalculator(props);
    }

    /** Spreads for dates in [from, to]; loads extra history before {@code from} for stale-spot lookup and day moves. */
    @Transactional(readOnly = true)
    public List<SpreadPoint> spreads(String commodity, LocalDate from, LocalDate to) {
        LocalDate lookback = from.minusDays(Math.max(props.maxSpotAgeDays(), 7));
        var snapshots = repository.findByCommodityAndPriceDateBetweenOrderByPriceDateAsc(commodity.toUpperCase(), lookback, to);
        return calculator.compute(commodity, snapshots).stream()
                .filter(p -> !p.date().isBefore(from))
                .toList();
    }

    public SpreadProperties properties() {
        return props;
    }
}
