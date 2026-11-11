package io.github.soham023.spottracker.source.agmarknet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.source.HttpFetcher;
import io.github.soham023.spottracker.source.PriceSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** APMC mandi modal prices from data.gov.in (Agmarknet feed). */
@Component
public class DataGovMandiSource implements PriceSource {

    public static final String ID = "AGMARKNET";
    private static final int MAX_PAGES = 20;

    private final DataGovMandiProperties props;
    private final HttpFetcher http;
    private final Clock clock;
    private final DataGovMandiParser parser;

    public DataGovMandiSource(DataGovMandiProperties props, HttpFetcher http, Clock clock, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.clock = clock;
        this.parser = new DataGovMandiParser(mapper);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        return props.enabled() && props.hasKey();
    }

    @Override
    public List<PriceSnapshot> fetch() throws Exception {
        Instant now = clock.instant();
        List<PriceSnapshot> all = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            String url = UriComponentsBuilder.fromUriString(props.baseUrl())
                    .path("/resource/{id}")
                    .queryParam("api-key", props.apiKey())
                    .queryParam("format", "json")
                    .queryParam("limit", props.pageSize())
                    .queryParam("offset", offset)
                    .queryParam("filters[commodity]", props.commodity())
                    .encode()
                    .buildAndExpand(props.resourceId())
                    .toUriString();
            DataGovMandiParser.Page result = parser.parse(http.get(url, props.timeout()), props.markets(), now);
            all.addAll(result.snapshots());
            offset += props.pageSize();
            if (offset >= result.total()) {
                break;
            }
        }
        return all;
    }
}
