package io.github.soham023.spottracker.alert;

import io.github.soham023.spottracker.contract.ContractDataWriter;
import io.github.soham023.spottracker.contract.ContractProperties;
import io.github.soham023.spottracker.contract.ContractService;
import io.github.soham023.spottracker.source.Fixtures;
import io.github.soham023.spottracker.source.ncdex.NcdexProductPage;
import io.github.soham023.spottracker.source.ncdex.NcdexProductParser;
import io.github.soham023.spottracker.source.ncdex.NcdexProperties;
import io.github.soham023.spottracker.spread.SpreadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Margin alerts over a run of product-page scrapes, end to end through the real writer, Flyway schema,
 * ContractService and AlertRepository: each scrape is stored, then alerts are evaluated as the
 * ingestion listener would.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:marginalerts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@Import({ContractDataWriter.class, ContractService.class, MarginAlertIntegrationTest.Props.class})
class MarginAlertIntegrationTest {

    @TestConfiguration
    static class Props {
        @Bean
        ContractProperties contractProperties() {
            return ContractProperties.defaults(); // alert above 2 percentage points
        }
    }

    private static final String SYMBOL = "TMCFGRNZM";
    private static final LocalDate DEC = LocalDate.of(2026, 12, 18);
    private static final LocalDate APR = LocalDate.of(2027, 4, 16);
    private static final LocalDate NOV_11 = LocalDate.of(2026, 11, 11);
    private static final LocalDate NOV_12 = LocalDate.of(2026, 11, 12);
    private static final LocalDate NOV_13 = LocalDate.of(2026, 11, 13);
    private static final LocalDate NOV_16 = LocalDate.of(2026, 11, 16); // Monday after a weekend with no margin file

    @Autowired
    ContractDataWriter writer;
    @Autowired
    ContractService contracts;
    @Autowired
    AlertRepository repository;

    private final NcdexProductPage template = new NcdexProductParser()
            .parse(Fixtures.read("ncdex-product-turmeric.html"), SYMBOL);

    private AlertService alerts;

    @BeforeEach
    void setUp() {
        SpreadService spreads = mock(SpreadService.class);
        when(spreads.spreads(anyString(), any(), any())).thenReturn(List.of());
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T13:00:00Z"), ZoneId.of("Asia/Kolkata"));
        alerts = new AlertService(spreads, repository, List.of(new LogAlertChannel()), contracts,
                new NcdexProperties(true, null, null, null, null, null), clock);
    }

    @Test
    void alertsEachNearMonthMoveOnceAndOnlyAgainstThePreviousMarginDate() {
        // Day 1: a single margin date, nothing to compare with
        scrape(NOV_11, "Morning", Map.of(DEC, "17.79", APR, "15.20"), 9);
        assertThat(alerts.evaluate("TURMERIC")).isEmpty();

        // Day 2: near month +10 pts -> one alert; evaluating again (a second ingestion run) adds nothing
        scrape(NOV_12, "Morning", Map.of(DEC, "27.79", APR, "15.20"), 9);
        assertThat(alerts.evaluate("TURMERIC")).singleElement().satisfies(a -> {
            assertThat(a.getAlertDate()).isEqualTo(NOV_12);
            assertThat(a.getLabel()).isEqualTo("2026-12-18");
            assertThat(a.getSpreadPct()).isEqualByComparingTo("10");
            assertThat(a.getMessage()).contains("raised from 17.79% to 27.79%", "on 2026-11-12 vs 2026-11-11");
        });
        assertThat(alerts.evaluate("TURMERIC")).isEmpty();

        // Day 3: near month +1 pt (under the threshold); a +5 pt hike on a far expiry is not alerted
        scrape(NOV_13, "Morning", Map.of(DEC, "28.79", APR, "20.20"), 9);
        assertThat(alerts.evaluate("TURMERIC")).isEmpty();

        // Day 4 after the weekend: the evening file supersedes the morning one, and the change is measured
        // against Friday's margin date, not against the morning file or against Day 2
        scrape(NOV_16, "Morning", Map.of(DEC, "28.79", APR, "20.20"), 9);
        scrape(NOV_16, "Evening", Map.of(DEC, "22.00", APR, "20.20"), 18);
        assertThat(alerts.evaluate("TURMERIC")).singleElement().satisfies(a -> {
            assertThat(a.getAlertDate()).isEqualTo(NOV_16);
            assertThat(a.getSpreadPct()).isEqualByComparingTo("-6.79");
            assertThat(a.getMessage()).contains("cut from 28.79% to 22.00%", "on 2026-11-16 vs 2026-11-13",
                    "(Evening margin file)");
        });
        assertThat(alerts.evaluate("TURMERIC")).isEmpty();

        assertThat(repository.findAll())
                .extracting(Alert::getAlertDate, Alert::getKind, Alert::getFlag, Alert::getLabel)
                .containsExactlyInAnyOrder(
                        tuple(NOV_12, AlertKind.MARGIN, AlertFlag.MARGIN_CHANGE, "2026-12-18"),
                        tuple(NOV_16, AlertKind.MARGIN, AlertFlag.MARGIN_CHANGE, "2026-12-18"));
    }

    /** Stores one product-page scrape whose margin table is dated {@code date} with the given TM per expiry. */
    private void scrape(LocalDate date, String fileType, Map<LocalDate, String> totalMargins, int hourUtc) {
        List<NcdexProductPage.Margin> margins = template.margins().stream()
                .filter(m -> totalMargins.containsKey(m.expiry()))
                .map(m -> new NcdexProductPage.Margin(fileType, date, m.expiry(), m.initialMargin(), m.extremeMargin(),
                        m.additionalPem(), new BigDecimal(totalMargins.get(m.expiry())), m.additionalLong(),
                        m.additionalShort(), m.specialLong(), m.specialShort()))
                .toList();
        assertThat(margins).hasSize(totalMargins.size());
        NcdexProductPage page = new NcdexProductPage(SYMBOL, template.lastTradedPrice(), template.change(),
                template.expiries(), margins, template.premiumDiscounts());
        writer.upsert(page, date, date.atTime(hourUtc, 0).toInstant(java.time.ZoneOffset.UTC));
    }
}
