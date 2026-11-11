package io.github.soham023.spottracker.alert;

import io.github.soham023.spottracker.contract.ContractService;
import io.github.soham023.spottracker.contract.MarginChange;
import io.github.soham023.spottracker.ingest.IngestionCompletedEvent;
import io.github.soham023.spottracker.source.ncdex.NcdexProperties;
import io.github.soham023.spottracker.spread.SpreadCalculator;
import io.github.soham023.spottracker.spread.SpreadFlag;
import io.github.soham023.spottracker.spread.SpreadKind;
import io.github.soham023.spottracker.spread.SpreadPoint;
import io.github.soham023.spottracker.spread.SpreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns flagged spread points into alerts. Each (commodity, date, kind, label, flag) is alerted at most
 * once, so re-running ingestion on the same day never re-sends the same message.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final Locale EN_IN = Locale.forLanguageTag("en-IN");

    private final SpreadService spreads;
    private final AlertRepository repository;
    private final List<AlertChannel> channels;
    private final ContractService contracts;
    private final String futuresSymbol;
    private final Clock clock;

    public AlertService(SpreadService spreads, AlertRepository repository, List<AlertChannel> channels,
                        ContractService contracts, NcdexProperties ncdex, Clock clock) {
        this.spreads = spreads;
        this.repository = repository;
        this.channels = channels;
        this.contracts = contracts;
        this.futuresSymbol = ncdex.futuresSymbol();
        this.clock = clock;
    }

    @EventListener
    public void onIngestion(IngestionCompletedEvent event) {
        if (event.run().anySucceeded()) {
            evaluate("TURMERIC");
        }
    }

    /**
     * Raises alerts for the most recent date that has spread data, and for a near-month total-margin change
     * on the latest margin date. Returns the newly created alerts.
     */
    public List<Alert> evaluate(String commodity) {
        LocalDate today = LocalDate.now(clock);
        List<SpreadPoint> latest = SpreadCalculator.latest(spreads.spreads(commodity, today.minusDays(14), today));
        List<Alert> created = new ArrayList<>();
        for (SpreadPoint p : latest) {
            for (SpreadFlag flag : p.flags()) {
                BigDecimal pct = flag == SpreadFlag.WIDE_SPREAD ? p.effectiveSpreadPct() : p.spreadPct();
                raise(new Alert(p.commodity(), p.date(), AlertKind.of(p.kind()), p.label(), AlertFlag.of(flag),
                        pct, message(p, flag), clock.instant())).ifPresent(created::add);
            }
        }
        BigDecimal threshold = contracts.properties().marginAlertPoints();
        contracts.nearMonthMarginChange(futuresSymbol)
                .filter(change -> change.exceeds(threshold))
                .flatMap(change -> raise(new Alert(commodity.toUpperCase(Locale.ROOT), change.marginDate(),
                        AlertKind.MARGIN, change.expiry().toString(), AlertFlag.MARGIN_CHANGE, change.change(),
                        marginMessage(commodity, change), clock.instant())))
                .ifPresent(created::add);
        return created;
    }

    private Optional<Alert> raise(Alert candidate) {
        if (repository.existsByDedupeKey(candidate.getDedupeKey())) {
            return Optional.empty();
        }
        Alert alert = repository.save(candidate);
        alert.markDelivered(dispatch(alert));
        repository.save(alert);
        return Optional.of(alert);
    }

    static String marginMessage(String commodity, MarginChange c) {
        String name = commodity.charAt(0) + commodity.substring(1).toLowerCase(Locale.ROOT);
        return "%s %s futures (%s, expiry %s): NCDEX total margin %s from %s%% to %s%% (%s pts) on %s vs %s. "
                .formatted(name, c.expiry().toString().substring(0, 7), c.symbol(), c.expiry(),
                        c.change().signum() > 0 ? "raised" : "cut", c.previousTotalMargin().toPlainString(),
                        c.totalMargin().toPlainString(), signed(c.change()), c.marginDate(), c.previousDate())
                + "Additional margin long %s%%, short %s%% (%s margin file)."
                .formatted(plain(c.additionalLong()), plain(c.additionalShort()), c.fileType());
    }

    private static String plain(BigDecimal v) {
        return v == null ? "–" : v.toPlainString();
    }

    private String dispatch(Alert alert) {
        List<String> delivered = new ArrayList<>();
        for (AlertChannel channel : channels) {
            if (!channel.isEnabled()) {
                continue;
            }
            try {
                channel.send(alert);
                delivered.add(channel.name());
            } catch (Exception e) {
                log.warn("Alert channel {} failed: {}", channel.name(), e.getMessage());
            }
        }
        return String.join(",", delivered);
    }

    static String message(SpreadPoint p, SpreadFlag flag) {
        String name = p.commodity().charAt(0) + p.commodity().substring(1).toLowerCase(Locale.ROOT);
        String what = switch (p.kind()) {
            case BASIS -> "%s basis (%s futures vs NCDEX spot)".formatted(name, p.label());
            case DELIVERY -> "%s delivery basis (near-month futures vs NCDEX %s spot)".formatted(name, p.label());
            case MANDI -> "%s at %s mandi vs NCDEX spot".formatted(name, p.label());
        };
        String detail = "%s ₹%s vs spot ₹%s polled %s".formatted(
                p.kind() == SpreadKind.MANDI ? "modal" : "futures", rupees(p.comparedPrice()), rupees(p.spotPrice()),
                p.spotDate());
        return switch (flag) {
            case WIDE_SPREAD -> p.adjustedSpreadPct() != null && p.adjustmentPct().signum() != 0
                    ? "%s on %s: %s%% (₹%s/qtl) after the NCDEX %s%% delivery %s; raw %s%%. %s".formatted(what, p.date(),
                    signed(p.adjustedSpreadPct()), rupees(p.adjustedSpread()), signed(p.adjustmentPct()),
                    p.adjustmentPct().signum() > 0 ? "premium" : "discount", signed(p.spreadPct()), detail)
                    : "%s on %s: %s%% (₹%s/qtl). %s".formatted(what, p.date(), signed(p.spreadPct()),
                    rupees(p.spread()), detail);
            case SHARP_MOVE -> "%s on %s: price moved %s%% day over day. %s".formatted(what, p.date(),
                    signed(p.priceMovePct()), detail);
        };
    }

    private static String signed(BigDecimal v) {
        return (v.signum() > 0 ? "+" : "") + v.toPlainString();
    }

    private static String rupees(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(EN_IN);
        f.setMinimumFractionDigits(0);
        f.setMaximumFractionDigits(2);
        return f.format(v);
    }
}
