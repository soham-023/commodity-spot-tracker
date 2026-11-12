package io.github.soham023.spottracker.seed;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceSnapshotRepository;
import io.github.soham023.spottracker.ingest.SnapshotWriter;
import io.github.soham023.spottracker.source.Prices;
import io.github.soham023.spottracker.source.ncdex.NcdexPriceSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Default profile: when the price table is empty, loads the real NCDEX turmeric history shipped in
 * {@code src/main/resources/seed} (see {@code seed/README.md}) so the dashboard has data on first start.
 * Rows go through {@link SnapshotWriter}, so a re-run updates instead of duplicating. Switch off with
 * {@code SPOT_SEED_ENABLED=false}. Never runs on the {@code demo} profile, which has its own (labelled) data.
 */
@Component
@Profile("!demo")
@Order(10)
@ConditionalOnProperty(prefix = "spot.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);
    static final String FUTURES_CSV = "seed/ncdex-turmeric-futures.csv";
    static final String SPOT_CSV = "seed/ncdex-turmeric-spot.csv";
    static final String COMMODITY = "TURMERIC";

    private final PriceSnapshotRepository repository;
    private final SnapshotWriter writer;
    private final Clock clock;

    public SeedDataLoader(PriceSnapshotRepository repository, SnapshotWriter writer, Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (repository.count() > 0) {
            log.info("Seed skipped: price table already has {} rows", repository.count());
            return;
        }
        Instant now = clock.instant();
        List<PriceSnapshot> futures = readFutures(FUTURES_CSV, now);
        List<PriceSnapshot> spot = readSpot(SPOT_CSV, now);
        var f = writer.upsert(futures);
        var s = writer.upsert(spot);
        log.info("Seeded real NCDEX turmeric history: {} futures rows ({} new), {} spot rows ({} new)",
                futures.size(), f.inserted(), spot.size(), s.inserted());
    }

    /** trade_date,symbol,expiry_date,centre,prev_close,open,high,low,close,volume,open_interest */
    public static List<PriceSnapshot> readFutures(String path, Instant fetchedAt) throws IOException {
        List<PriceSnapshot> out = new ArrayList<>();
        for (String[] c : rows(path)) {
            BigDecimal close = Prices.parse(c[8]);
            if (close == null || close.signum() <= 0) {
                continue;
            }
            String month = c[2].substring(0, 7);
            out.add(PriceSnapshot.futures(COMMODITY, NcdexPriceSource.ID, c[3], month, close, LocalDate.parse(c[0]),
                    fetchedAt).withRange(positive(c[7]), positive(c[6])));
        }
        return out;
    }

    /** date,symbol,centre,variety,price,origin,polled_at_ist */
    public static List<PriceSnapshot> readSpot(String path, Instant fetchedAt) throws IOException {
        List<PriceSnapshot> out = new ArrayList<>();
        for (String[] c : rows(path)) {
            BigDecimal price = Prices.parse(c[4]);
            if (price == null || price.signum() <= 0) {
                continue;
            }
            out.add(PriceSnapshot.spot(COMMODITY, NcdexPriceSource.ID, c[2], c[3], price, LocalDate.parse(c[0]), fetchedAt));
        }
        return out;
    }

    private static BigDecimal positive(String raw) {
        BigDecimal v = Prices.parse(raw);
        return v == null || v.signum() <= 0 ? null : v;
    }

    private static List<String[]> rows(String path) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            boolean header = true;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                out.add(line.split(",", -1));
            }
        }
        return out;
    }
}
