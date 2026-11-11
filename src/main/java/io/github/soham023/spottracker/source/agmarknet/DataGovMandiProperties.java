package io.github.soham023.spottracker.source.agmarknet;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * data.gov.in "Current Daily Price of Various Commodities from Various Markets (Mandi)" API,
 * which republishes Agmarknet APMC arrivals. Needs an API key ({@code DATA_GOV_API_KEY});
 * the source stays disabled without one.
 *
 * @param markets market-name fragments to keep (case-insensitive "contains"); empty keeps every market
 */
@ConfigurationProperties(prefix = "spot.sources.agmarknet")
public record DataGovMandiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String resourceId,
        String commodity,
        List<String> markets,
        int pageSize,
        Duration timeout) {

    public DataGovMandiProperties {
        baseUrl = baseUrl == null ? "https://api.data.gov.in" : baseUrl;
        resourceId = resourceId == null ? "9ef84268-d588-465a-a308-a864a43d0070" : resourceId;
        commodity = commodity == null ? "Turmeric" : commodity;
        markets = markets == null ? List.of() : markets;
        pageSize = pageSize <= 0 ? 1000 : pageSize;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
