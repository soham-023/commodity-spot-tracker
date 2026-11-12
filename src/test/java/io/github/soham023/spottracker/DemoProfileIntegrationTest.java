package io.github.soham023.spottracker;

import io.github.soham023.spottracker.alert.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the whole app on the demo profile: Flyway, demo loader, spread analysis, alerts, REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoProfileIntegrationTest {

    /** The recorded data is from 2026-11-11; pin "today" so near month and windows don't drift after 18 Dec 2026. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-11T13:00:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    AlertService alerts;

    @Test
    void summaryReflectsRecordedNcdexData() throws Exception {
        mvc.perform(get("/api/v1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ncdexSpot.price").value(24954.10))
                .andExpect(jsonPath("$.ncdexSpot.date").value("2026-11-04"))
                .andExpect(jsonPath("$.nearMonthFutures.contractMonth").value("2026-12"))
                .andExpect(jsonPath("$.spreads[?(@.kind == 'BASIS')].spreadPct").value(14.45))
                .andExpect(jsonPath("$.mandis.length()").value(6));
    }

    @Test
    void alertsAreNotRaisedTwiceForTheSameDay() throws Exception {
        assertThat(alerts.evaluate("TURMERIC")).isEmpty();
        mvc.perform(get("/api/v1/alerts").param("from", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)));
    }

    @Test
    void dashboardAndApiDocsAreServed() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("priceChart")));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/spreads")))
                .andExpect(content().string(containsString("/api/v1/contracts")))
                .andExpect(content().string(containsString("/api/v1/premium-discount")));
    }

    @Test
    void contractsExposeTheRecordedCalendarAndMargins() throws Exception {
        mvc.perform(get("/api/v1/contracts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("TMCFGRNZM"))
                .andExpect(jsonPath("$.productUrl").value("https://www.ncdex.com/products/TMCFGRNZM"))
                .andExpect(jsonPath("$.marginDate").value("2026-11-11"))
                .andExpect(jsonPath("$.contracts[?(@.expiry == '2026-12-18')].margin.total").value(17.79))
                .andExpect(jsonPath("$.contracts[?(@.expiry == '2026-12-18')].margin.additionalLong").value(10.0))
                .andExpect(jsonPath("$.contracts[?(@.expiry == '2027-05-20')].margin.total").value(15.2));
        mvc.perform(get("/api/v1/contracts").param("symbol", "TM;--")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/contracts").param("symbol", "JEERAUNJHA")).andExpect(status().isNotFound());
    }

    @Test
    void premiumDiscountMatrixAndAdjustedSpreads() throws Exception {
        mvc.perform(get("/api/v1/premium-discount").param("expiry", "2026-12-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("Farmer polished fingers Nizamabad quality at Nizamabad"))
                .andExpect(jsonPath("$.rows.length()").value(6))
                .andExpect(jsonPath("$.rows[?(@.quality == 'Rajapore')].divisor").value(1.04))
                .andExpect(jsonPath("$.rows[?(@.quality == 'Unpolished Fingers')].premiumPct").value(-4.0));
        mvc.perform(get("/api/v1/premium-discount").param("expiry", "2030-01-01")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spreads[?(@.label == 'Rajapore @ Sangli')].adjustmentPct").value(4.0))
                .andExpect(jsonPath("$.spreads[?(@.kind == 'BASIS')].adjustedSpreadPct").value(14.45));
    }

    @Test
    void unknownCommodityIs404() throws Exception {
        mvc.perform(get("/api/v1/summary").param("commodity", "Saffron")).andExpect(status().isNotFound());
    }
}
