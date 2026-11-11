package io.github.soham023.spottracker.spread;

import io.github.soham023.spottracker.contract.ContractCalendar;
import io.github.soham023.spottracker.contract.ContractService;
import io.github.soham023.spottracker.domain.PriceSnapshotRepository;
import io.github.soham023.spottracker.source.ncdex.NcdexProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SpreadService {

    private final PriceSnapshotRepository repository;
    private final SpreadProperties props;
    private final SpreadCalculator calculator;
    private final ContractService contracts;
    private final String futuresSymbol;

    public SpreadService(PriceSnapshotRepository repository, SpreadProperties props, ContractService contracts,
                         NcdexProperties ncdex) {
        this.repository = repository;
        this.props = props;
        this.contracts = contracts;
        this.futuresSymbol = ncdex.futuresSymbol();
        this.calculator = new SpreadCalculator(props);
    }

    /** Spreads for dates in [from, to]; loads extra history before {@code from} for stale-spot lookup and day moves. */
    @Transactional(readOnly = true)
    public List<SpreadPoint> spreads(String commodity, LocalDate from, LocalDate to) {
        LocalDate lookback = from.minusDays(Math.max(props.maxSpotAgeDays(), 7));
        var snapshots = repository.findByCommodityAndPriceDateBetweenOrderByPriceDateAsc(commodity.toUpperCase(), lookback, to);
        return calculator.compute(commodity, snapshots, calendar(), contracts.deliveryMatrix(futuresSymbol)).stream()
                .filter(p -> !p.date().isBefore(from))
                .toList();
    }

    /** Expiry calendar of the futures contract the basis is measured against. */
    public ContractCalendar calendar() {
        return contracts.calendar(futuresSymbol);
    }

    public SpreadProperties properties() {
        return props;
    }
}
