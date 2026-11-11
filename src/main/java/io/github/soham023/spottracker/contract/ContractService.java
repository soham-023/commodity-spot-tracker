package io.github.soham023.spottracker.contract;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the scraped contract data. */
@Service
@Transactional(readOnly = true)
public class ContractService {

    private final ContractExpiryRepository expiries;
    private final ContractProperties props;

    public ContractService(ContractExpiryRepository expiries, ContractProperties props) {
        this.expiries = expiries;
        this.props = props;
    }

    /** Every expiry ever seen for {@code symbol}; empty until the product page has been scraped once. */
    public ContractCalendar calendar(String symbol) {
        return new ContractCalendar(expiries.findBySymbolOrderByExpiryDateAsc(symbol).stream()
                .map(ContractExpiry::getExpiryDate).toList(), props.rollDays());
    }

    public ContractProperties properties() {
        return props;
    }
}
