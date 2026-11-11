package io.github.soham023.spottracker.api;

import io.github.soham023.spottracker.api.dto.ContractsResponse;
import io.github.soham023.spottracker.api.dto.PremiumDiscountResponse;
import io.github.soham023.spottracker.contract.ContractCalendar;
import io.github.soham023.spottracker.contract.ContractExpiry;
import io.github.soham023.spottracker.contract.ContractService;
import io.github.soham023.spottracker.contract.DailyMargin;
import io.github.soham023.spottracker.contract.MarginChange;
import io.github.soham023.spottracker.contract.PremiumDiscount;
import io.github.soham023.spottracker.query.NotFoundException;
import io.github.soham023.spottracker.source.ncdex.NcdexProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Contracts")
public class ContractController {

    private static final String SYMBOL_PATTERN = "^[A-Za-z0-9]{3,20}$";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ContractService contracts;
    private final NcdexProperties ncdex;
    private final Clock clock;

    public ContractController(ContractService contracts, NcdexProperties ncdex, Clock clock) {
        this.contracts = contracts;
        this.ncdex = ncdex;
        this.clock = clock;
    }

    @GetMapping("/contracts")
    @Operation(summary = "NCDEX futures expiries with the latest daily margins", description =
            "From the NCDEX product page. Margins are % of contract value (IM, ELM, additional/pre-expiry, total). "
                    + "nearMonth is the contract the basis uses today: the first expiry more than rollDays away.")
    public ContractsResponse contracts(
            @RequestParam(defaultValue = "TMCFGRNZM") @Pattern(regexp = SYMBOL_PATTERN) String symbol) {
        String s = symbol.toUpperCase(Locale.ROOT);
        List<ContractExpiry> expiries = contracts.expiries(s);
        if (expiries.isEmpty()) {
            throw new NotFoundException("No contract calendar recorded for " + s
                    + "; it is filled by the NCDEX product page source on the next ingestion run");
        }
        LocalDate today = LocalDate.now(clock);
        ContractCalendar calendar = contracts.calendar(s);
        LocalDate near = calendar.nearMonth(today).orElse(null);
        List<DailyMargin> recent = contracts.recentMargins(s, 10);
        LocalDate marginDate = recent.isEmpty() ? null : recent.getFirst().getMarginDate();
        LocalDate lastScrape = expiries.stream().map(ContractExpiry::getLastSeen).max(LocalDate::compareTo).orElse(today);

        Map<LocalDate, DailyMargin> latestByExpiry = new LinkedHashMap<>();
        recent.stream().filter(m -> m.getMarginDate().equals(marginDate))
                .forEach(m -> latestByExpiry.putIfAbsent(m.getExpiryDate(), m));

        List<ContractsResponse.Contract> rows = expiries.stream()
                .filter(e -> !e.getExpiryDate().isBefore(today))
                .map(e -> {
                    DailyMargin m = latestByExpiry.get(e.getExpiryDate());
                    BigDecimal change = MarginChange.latest(recent, e.getExpiryDate()).map(MarginChange::change)
                            .orElse(null);
                    return new ContractsResponse.Contract(e.getExpiryDate(), ContractCalendar.month(e.getExpiryDate()),
                            ChronoUnit.DAYS.between(today, e.getExpiryDate()), e.getExpiryDate().equals(near),
                            calendar.inRollWindow(e.getExpiryDate(), today), e.getLastSeen().equals(lastScrape),
                            m == null ? null : ContractsResponse.Margin.from(m), change);
                })
                .toList();
        return new ContractsResponse(s, productUrl(s), today, calendar.rollDays(), near, marginDate, rows);
    }

    @GetMapping("/premium-discount")
    @Operation(summary = "NCDEX premium/discount matrix by delivery location", description =
            "Premium (+) or discount (−) in % over the contract's basis quality, as last published on the NCDEX "
                    + "product page. A spot quote of a grade divided by 'divisor' is its basis-quality equivalent.")
    public PremiumDiscountResponse premiumDiscount(
            @RequestParam(defaultValue = "TMCFGRNZM") @Pattern(regexp = SYMBOL_PATTERN) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiry) {
        String s = symbol.toUpperCase(Locale.ROOT);
        List<PremiumDiscount> rows = contracts.latestMatrixRows(s).stream()
                .filter(p -> expiry == null || p.getExpiryDate().equals(expiry))
                .toList();
        if (rows.isEmpty()) {
            throw new NotFoundException(expiry == null
                    ? "No premium/discount matrix recorded for " + s
                    : "No premium/discount matrix recorded for " + s + " expiry " + expiry);
        }
        String basis = rows.stream().filter(PremiumDiscount::isBasis).map(PremiumDiscount::getLocation).findFirst()
                .orElse(rows.getFirst().getBasisLocation());
        return new PremiumDiscountResponse(s, rows.getFirst().getAsOfDate(), basis, productUrl(s), rows.stream()
                .map(p -> new PremiumDiscountResponse.Row(p.getExpiryDate(), p.getLocation(), p.getQuality(),
                        p.getCentre(), p.getPremiumPct(), p.isBasis(),
                        BigDecimal.ONE.add(p.getPremiumPct().divide(HUNDRED, 4, RoundingMode.HALF_UP))))
                .toList());
    }

    private String productUrl(String symbol) {
        return ncdex.baseUrl() + "/products/" + symbol;
    }
}
