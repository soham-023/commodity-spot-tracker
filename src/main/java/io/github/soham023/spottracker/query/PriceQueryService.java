package io.github.soham023.spottracker.query;

import io.github.soham023.spottracker.alert.AlertRepository;
import io.github.soham023.spottracker.api.dto.AlertDto;
import io.github.soham023.spottracker.api.dto.PageResponse;
import io.github.soham023.spottracker.api.dto.PriceDto;
import io.github.soham023.spottracker.api.dto.SummaryResponse;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceSnapshotRepository;
import io.github.soham023.spottracker.domain.PriceSnapshotSpecs;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.source.agmarknet.DataGovMandiSource;
import io.github.soham023.spottracker.source.ncdex.NcdexPriceSource;
import io.github.soham023.spottracker.spread.SpreadCalculator;
import io.github.soham023.spottracker.spread.SpreadService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PriceQueryService {

    private final PriceSnapshotRepository prices;
    private final AlertRepository alerts;
    private final SpreadService spreads;

    public PriceQueryService(PriceSnapshotRepository prices, AlertRepository alerts, SpreadService spreads) {
        this.prices = prices;
        this.alerts = alerts;
        this.spreads = spreads;
    }

    public PageResponse<PriceDto> prices(String commodity, String source, String market, PriceType type,
                                         LocalDate from, LocalDate to, int page, int size) {
        var sort = Sort.by(Sort.Order.desc("priceDate"), Sort.Order.asc("source"), Sort.Order.asc("market"),
                Sort.Order.asc("contractMonth"));
        var result = prices.findAll(PriceSnapshotSpecs.filter(commodity, source, market, type, from, to),
                PageRequest.of(page, size, sort));
        return PageResponse.of(result, PriceDto::from);
    }

    public PageResponse<AlertDto> alerts(LocalDate from, int page, int size) {
        return PageResponse.of(alerts.findByAlertDateGreaterThanEqualOrderByAlertDateDescIdDesc(from,
                PageRequest.of(page, size)), AlertDto::from);
    }

    public SummaryResponse summary(String commodity, LocalDate today) {
        String c = commodity.toUpperCase();
        LocalDate latest = prices.findLatestDate(c)
                .orElseThrow(() -> new NotFoundException("No prices recorded for commodity " + commodity));
        LocalDate windowStart = latest.minusDays(14);
        List<PriceSnapshot> window = prices.findByCommodityAndPriceDateBetweenOrderByPriceDateAsc(c, windowStart, latest);
        var props = spreads.properties();

        PriceDto spot = window.stream()
                .filter(s -> s.getSource().equals(NcdexPriceSource.ID) && s.getPriceType() == PriceType.SPOT
                        && s.getMarket().equalsIgnoreCase(props.benchmarkMarket())
                        && s.getVariety().equalsIgnoreCase(props.benchmarkVariety()))
                .max(Comparator.comparing(PriceSnapshot::getPriceDate)).map(PriceDto::from).orElse(null);

        PriceDto futures = window.stream()
                .filter(s -> s.getSource().equals(NcdexPriceSource.ID) && s.getPriceType() == PriceType.FUTURES)
                .max(Comparator.comparing(PriceSnapshot::getPriceDate)
                        .thenComparing(PriceSnapshot::getContractMonth, Comparator.reverseOrder()))
                .map(PriceDto::from).orElse(null);

        Map<String, PriceSnapshot> latestPerMandi = new LinkedHashMap<>();
        window.stream()
                .filter(s -> s.getSource().equals(DataGovMandiSource.ID))
                .sorted(Comparator.comparing(PriceSnapshot::getMarket).thenComparing(PriceSnapshot::getPriceDate))
                .forEach(s -> latestPerMandi.merge(s.getMarket() + "|" + s.getVariety(), s,
                        (a, b) -> b.getPriceDate().isAfter(a.getPriceDate()) ? b : a));

        var spreadPoints = SpreadCalculator.latest(spreads.spreads(c, windowStart, latest));
        var recent = alerts.findByAlertDateGreaterThanEqualOrderByAlertDateDescIdDesc(today.minusDays(30),
                PageRequest.of(0, 10)).map(AlertDto::from).getContent();

        return new SummaryResponse(c, latest, spot, futures,
                latestPerMandi.values().stream().map(PriceDto::from).toList(), spreadPoints, recent);
    }
}
