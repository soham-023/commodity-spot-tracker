package io.github.soham023.spottracker.demo;

import io.github.soham023.spottracker.alert.AlertService;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.ingest.SnapshotWriter;
import io.github.soham023.spottracker.source.Prices;
import io.github.soham023.spottracker.source.ncdex.NcdexFuturesParser;
import io.github.soham023.spottracker.source.ncdex.NcdexSpotParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code --spring.profiles.active=demo}: fills the database so the dashboard has something to show
 * without network access or API keys.
 * <ol>
 *   <li>{@code demo/turmeric-demo-history.csv}: ~10 weeks of <b>synthetic</b> spot, futures and mandi prices.</li>
 *   <li>{@code demo/ncdex-*.html}: NCDEX pages <b>recorded</b> on 2026-11-11, parsed by the real parsers.</li>
 * </ol>
 * Then runs alert evaluation so the alert list is populated too.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final SnapshotWriter writer;
    private final AlertService alerts;
    private final Clock clock;

    public DemoDataLoader(SnapshotWriter writer, AlertService alerts, Clock clock) {
        this.writer = writer;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Instant now = clock.instant();
        List<PriceSnapshot> history = readCsv("demo/turmeric-demo-history.csv", now);
        var h = writer.upsert(history);

        List<PriceSnapshot> recorded = new ArrayList<>();
        recorded.addAll(new NcdexSpotParser().parse(read("demo/ncdex-live-spot-turmeric.html"), now));
        recorded.addAll(new NcdexFuturesParser().parse(read("demo/ncdex-live-quotes.html"), "TMCFGRNZM",
                "TURMERIC", "Nizamabad", now));
        var r = writer.upsert(recorded);

        int raised = alerts.evaluate("TURMERIC").size();
        log.info("Demo data loaded: {} synthetic history rows ({} new), {} recorded NCDEX rows ({} new), {} alerts raised",
                history.size(), h.inserted(), recorded.size(), r.inserted(), raised);
    }

    static List<PriceSnapshot> readCsv(String path, Instant fetchedAt) throws IOException {
        List<PriceSnapshot> out = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] c = line.split(",", -1);
                PriceType type = PriceType.valueOf(c[4]);
                out.add(new PriceSnapshot("TURMERIC", c[1], c[2], c[3], type, c[5], Prices.parse(c[6]),
                        LocalDate.parse(c[0]), fetchedAt).withRange(Prices.parse(c[7]), Prices.parse(c[8])));
            }
        }
        return out;
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
