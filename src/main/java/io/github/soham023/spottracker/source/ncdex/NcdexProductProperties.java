package io.github.soham023.spottracker.source.ncdex;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The NCDEX product page for the futures symbol in {@link NcdexProperties#futuresSymbol()}
 * ({@code <base-url>/products/<symbol>}): contract calendar, daily margins and the premium/discount matrix.
 *
 * @param enabled switch the scrape on/off ({@code SPOT_NCDEX_PRODUCT_ENABLED})
 * @param timeout per-request timeout
 */
@ConfigurationProperties(prefix = "spot.sources.ncdex-product")
public record NcdexProductProperties(boolean enabled, Duration timeout) {

    public NcdexProductProperties {
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}
