package io.github.soham023.spottracker.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the database holds for a commodity: drives the dashboard's default range, selectors and empty state. */
public record DataStatus(String commodity, long rows, LocalDate firstDate, LocalDate lastDate, Instant lastUpdated,
                         List<String> contractMonths) {
}
