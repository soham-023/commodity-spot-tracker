package io.github.soham023.spottracker.contract;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param rollDays calendar days before expiry at which the near month rolls to the next contract
 *                 ({@code SPOT_ROLL_DAYS}, default 5)
 */
@ConfigurationProperties(prefix = "spot.contracts")
public record ContractProperties(Integer rollDays) {

    public ContractProperties {
        rollDays = rollDays == null ? 5 : rollDays;
        if (rollDays < 0) {
            throw new IllegalArgumentException("spot.contracts.roll-days must not be negative");
        }
    }

    public static ContractProperties defaults() {
        return new ContractProperties(null);
    }
}
