package io.github.soham023.spottracker;

import io.github.soham023.spottracker.alert.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(content().string(containsString("/api/v1/spreads")));
    }

    @Test
    void unknownCommodityIs404() throws Exception {
        mvc.perform(get("/api/v1/summary").param("commodity", "Saffron")).andExpect(status().isNotFound());
    }
}
