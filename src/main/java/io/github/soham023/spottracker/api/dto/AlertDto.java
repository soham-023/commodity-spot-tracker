package io.github.soham023.spottracker.api.dto;

import io.github.soham023.spottracker.alert.Alert;
import io.github.soham023.spottracker.alert.AlertFlag;
import io.github.soham023.spottracker.alert.AlertKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AlertDto(Long id, String commodity, LocalDate date, AlertKind kind, String label, AlertFlag flag,
                       BigDecimal spreadPct, String message, String deliveredTo, Instant createdAt) {

    public static AlertDto from(Alert a) {
        return new AlertDto(a.getId(), a.getCommodity(), a.getAlertDate(), a.getKind(), a.getLabel(), a.getFlag(),
                a.getSpreadPct(), a.getMessage(), a.getDeliveredTo(), a.getCreatedAt());
    }
}
