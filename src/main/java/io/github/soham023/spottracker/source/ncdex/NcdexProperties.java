package io.github.soham023.spottracker.source.ncdex;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * NCDEX public web pages. No key is needed; the pages are plain server-rendered HTML.
 *
 * @param enabled       switch the source on/off ({@code SPOT_NCDEX_ENABLED})
 * @param baseUrl       site root
 * @param spotNames     product names to request on the Live Spot Quotes page
 * @param futuresSymbol futures symbol to read from Live Quotes (Turmeric = TMCFGRNZM)
 * @param basisCentre   delivery centre the futures contract is quoted for
 * @param timeout       per-request timeout
 */
@ConfigurationProperties(prefix = "spot.sources.ncdex")
public record NcdexProperties(
        boolean enabled,
        String baseUrl,
        List<String> spotNames,
        String futuresSymbol,
        String basisCentre,
        Duration timeout) {

    public NcdexProperties {
        baseUrl = baseUrl == null ? "https://www.ncdex.com" : baseUrl;
        spotNames = spotNames == null || spotNames.isEmpty()
                ? List.of("Turmeric (Farmer Polished)", "Turmeric (Unpolished)", "Turmeric (Rajapore)")
                : spotNames;
        futuresSymbol = futuresSymbol == null ? "TMCFGRNZM" : futuresSymbol;
        basisCentre = basisCentre == null ? "Nizamabad" : basisCentre;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}
