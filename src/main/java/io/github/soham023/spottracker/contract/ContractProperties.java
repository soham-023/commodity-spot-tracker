package io.github.soham023.spottracker.contract;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * @param rollDays           calendar days before expiry at which the near month rolls to the next contract
 *                           ({@code SPOT_ROLL_DAYS}, default 5)
 * @param marginAlertPoints  alert when the near month's total margin (TM, % of contract value) changes by more
 *                           than this many percentage points from the previous margin date
 *                           ({@code SPOT_MARGIN_ALERT_POINTS}, default 2)
 */
@ConfigurationProperties(prefix = "spot.contracts")
public record ContractProperties(Integer rollDays, BigDecimal marginAlertPoints) {

    public ContractProperties {
        rollDays = rollDays == null ? 5 : rollDays;
        marginAlertPoints = marginAlertPoints == null ? new BigDecimal("2") : marginAlertPoints;
        if (marginAlertPoints.signum() < 0) {
            throw new IllegalArgumentException("spot.contracts.margin-alert-points must not be negative");
        }
        if (rollDays < 0) {
            throw new IllegalArgumentException("spot.contracts.roll-days must not be negative");
        }
    }

    public static ContractProperties defaults() {
        return new ContractProperties(null, null);
    }
}
