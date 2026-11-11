package io.github.soham023.spottracker.api.dto;

import io.github.soham023.spottracker.spread.SpreadPoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Latest picture for one commodity: the NCDEX benchmark spot, the near-month futures, every tracked
 * mandi, the spreads between them and the most recent alerts.
 */
public record SummaryResponse(
        String commodity,
        LocalDate asOf,
        PriceDto ncdexSpot,
        PriceDto nearMonthFutures,
        List<PriceDto> mandis,
        List<SpreadPoint> spreads,
        List<AlertDto> recentAlerts) {
}
