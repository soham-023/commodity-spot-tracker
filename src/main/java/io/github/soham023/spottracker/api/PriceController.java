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

    @GetMapping("/spreads")
    @Operation(summary = "Basis and mandi spreads", description =
            "BASIS = near-month futures − NCDEX spot; MANDI = APMC modal − NCDEX spot. Flags mark spreads above the threshold or sharp daily moves.")
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
