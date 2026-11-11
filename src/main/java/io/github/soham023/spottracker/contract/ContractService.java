package io.github.soham023.spottracker.contract;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read side of the scraped contract data. */
@Service
@Transactional(readOnly = true)
public class ContractService {

    private final ContractExpiryRepository expiries;
    private final DailyMarginRepository margins;
    private final PremiumDiscountRepository matrix;
    private final ContractProperties props;

    public ContractService(ContractExpiryRepository expiries, DailyMarginRepository margins,
                           PremiumDiscountRepository matrix, ContractProperties props) {
        this.expiries = expiries;
        this.margins = margins;
        this.matrix = matrix;
        this.props = props;
    }

    public List<ContractExpiry> expiries(String symbol) {
        return expiries.findBySymbolOrderByExpiryDateAsc(symbol);
    }

    /** Every expiry ever seen for {@code symbol}; empty until the product page has been scraped once. */
    public ContractCalendar calendar(String symbol) {
        return new ContractCalendar(expiries.findBySymbolOrderByExpiryDateAsc(symbol).stream()
                .map(ContractExpiry::getExpiryDate).toList(), props.rollDays());
    }

    /** The most recently published premium/discount matrix, every expiry; empty before the first scrape. */
    public List<PremiumDiscount> latestMatrixRows(String symbol) {
        return matrix.findLatestAsOf(symbol)
                .map(d -> matrix.findBySymbolAndAsOfDateOrderByExpiryDateAscIdAsc(symbol, d))
                .orElse(List.of());
    }

    public DeliveryMatrix deliveryMatrix(String symbol) {
        return new DeliveryMatrix(latestMatrixRows(symbol).stream()
                .map(p -> new DeliveryMatrix.Entry(p.getExpiryDate(), p.getQuality(), p.getCentre(), p.getPremiumPct(),
                        p.isBasis()))
                .toList());
    }

    /** Margin rows from the last {@code days} margin dates up to the latest one, newest first. */
    public List<DailyMargin> recentMargins(String symbol, int days) {
        return margins.findLatestMarginDate(symbol)
                .map(d -> margins.findBySymbolAndMarginDateBetweenOrderByMarginDateDescFetchedAtDescIdDesc(
                        symbol, d.minusDays(days), d))
                .orElse(List.of());
    }

    /**
     * Day-over-day total-margin change on the near-month contract as of the latest margin date, if two
     * margin dates within the last 10 days are stored.
     */
    public Optional<MarginChange> nearMonthMarginChange(String symbol) {
        List<DailyMargin> recent = recentMargins(symbol, 10);
        if (recent.isEmpty()) {
            return Optional.empty();
        }
        LocalDate latest = recent.getFirst().getMarginDate();
        return calendar(symbol).nearMonth(latest).flatMap(expiry -> MarginChange.latest(recent, expiry));
    }

    public ContractProperties properties() {
        return props;
    }
}
