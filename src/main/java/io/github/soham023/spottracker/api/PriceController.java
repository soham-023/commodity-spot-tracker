package io.github.soham023.spottracker.api;

import io.github.soham023.spottracker.api.dto.AlertDto;
import io.github.soham023.spottracker.api.dto.PageResponse;
import io.github.soham023.spottracker.api.dto.PriceDto;
import io.github.soham023.spottracker.api.dto.SummaryResponse;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.query.PriceQueryService;
import io.github.soham023.spottracker.spread.SpreadKind;
import io.github.soham023.spottracker.spread.SpreadPoint;
import io.github.soham023.spottracker.spread.SpreadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Prices & spreads")
public class PriceController {

    private static final String COMMODITY_PATTERN = "^[A-Za-z][A-Za-z ]{1,39}$";
    private static final int DEFAULT_WINDOW_DAYS = 90;

    private final PriceQueryService query;
    private final SpreadService spreads;
    private final Clock clock;

    public PriceController(PriceQueryService query, SpreadService spreads, Clock clock) {
        this.query = query;
        this.spreads = spreads;
        this.clock = clock;
    }

    @GetMapping("/prices")
    @Operation(summary = "Price history", description = "All prices in ₹/quintal, newest first. Every filter is optional.")
    public PageResponse<PriceDto> prices(
            @RequestParam(defaultValue = "TURMERIC") @Pattern(regexp = COMMODITY_PATTERN) String commodity,
            @Parameter(description = "NCDEX or AGMARKNET") @RequestParam(required = false) String source,
            @Parameter(description = "Centre / mandi, e.g. Nizamabad") @RequestParam(required = false) String market,
            @RequestParam(required = false) PriceType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        DateRange range = DateRange.of(from, to, LocalDate.now(clock), DEFAULT_WINDOW_DAYS);
        return query.prices(commodity, source, market, type, range.from(), range.to(), page, size);
    }

    @GetMapping("/prices/status")
    @Operation(summary = "Row count, first/last price date, last update time and futures contract months")
    public io.github.soham023.spottracker.api.dto.DataStatus status(
            @RequestParam(defaultValue = "TURMERIC") @Pattern(regexp = COMMODITY_PATTERN) String commodity) {
        return query.status(commodity);
    }

    @GetMapping(value = "/prices/export.csv", produces = "text/csv")
    @Operation(summary = "All prices in a date range as CSV (oldest first)", description =
            "Defaults: to = today, from = to - 90 days. Optional type (SPOT/FUTURES) and contract (yyyy-MM) filters.")
    public org.springframework.http.ResponseEntity<String> exportCsv(
            @RequestParam(defaultValue = "TURMERIC") @Pattern(regexp = COMMODITY_PATTERN) String commodity,
            @RequestParam(required = false) PriceType type,
            @RequestParam(required = false) @Pattern(regexp = "^\\d{4}-\\d{2}$") String contract,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange range = DateRange.of(from, to, LocalDate.now(clock), DEFAULT_WINDOW_DAYS);
        StringBuilder csv = new StringBuilder(
                "date,commodity,source,price_type,market,variety,contract_month,price,min_price,max_price,fetched_at\n");
        for (var p : query.export(commodity, type, contract, range.from(), range.to())) {
            csv.append(p.date()).append(',').append(p.commodity()).append(',').append(p.source()).append(',')
                    .append(p.priceType()).append(',').append(csvCell(p.market())).append(',')
                    .append(csvCell(p.variety())).append(',').append(nz(p.contractMonth())).append(',')
                    .append(p.price().toPlainString()).append(',').append(plain(p.minPrice())).append(',')
                    .append(plain(p.maxPrice())).append(',').append(p.fetchedAt()).append('\n');
        }
        String name = "%s-prices-%s-to-%s.csv".formatted(commodity.toLowerCase(java.util.Locale.ROOT), range.from(), range.to());
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    private static String csvCell(String s) {
        if (s == null) {
            return "";
        }
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    @GetMapping("/spreads")
    @Operation(summary = "Basis, delivery-grade and mandi spreads", description =
            "BASIS = near-month futures − NCDEX spot (basis quality); DELIVERY = near-month futures − another NCDEX "
                    + "deliverable grade; MANDI = APMC modal − NCDEX spot. spread/spreadPct are raw; adjustedSpread/"
                    + "adjustedSpreadPct convert the grade to basis-quality equivalent with the NCDEX premium/discount "
                    + "matrix (null when the matrix does not price it). Flags mark spreads above the threshold "
                    + "(adjusted when available) or sharp daily moves.")
    public List<SpreadPoint> spreads(
            @RequestParam(defaultValue = "TURMERIC") @Pattern(regexp = COMMODITY_PATTERN) String commodity,
            @RequestParam(required = false) SpreadKind kind,
            @RequestParam(defaultValue = "false") boolean flaggedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DateRange range = DateRange.of(from, to, LocalDate.now(clock), DEFAULT_WINDOW_DAYS);
        return spreads.spreads(commodity, range.from(), range.to()).stream()
                .filter(p -> kind == null || p.kind() == kind)
                .filter(p -> !flaggedOnly || p.flagged())
                .toList();
    }

    @GetMapping("/alerts")
    @Operation(summary = "Raised alerts, newest first")
    public PageResponse<AlertDto> alerts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        LocalDate start = from == null ? LocalDate.now(clock).minusDays(DEFAULT_WINDOW_DAYS) : from;
        return query.alerts(start, page, size);
    }

    @GetMapping("/summary")
    @Operation(summary = "Latest spot, futures, mandi prices, spreads and alerts for one commodity")
    public SummaryResponse summary(
            @RequestParam(defaultValue = "TURMERIC") @Pattern(regexp = COMMODITY_PATTERN) String commodity) {
        return query.summary(commodity, LocalDate.now(clock));
    }
}
