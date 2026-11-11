package io.github.soham023.spottracker.contract;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side of the scraped contract data. */
@Service
@Transactional(readOnly = true)
public class ContractService {

    private final ContractExpiryRepository expiries;
    private final PremiumDiscountRepository matrix;
    private final ContractProperties props;

    public ContractService(ContractExpiryRepository expiries, PremiumDiscountRepository matrix, ContractProperties props) {
        this.expiries = expiries;
        this.matrix = matrix;
        this.props = props;
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

    public ContractProperties properties() {
        return props;
    }
}
