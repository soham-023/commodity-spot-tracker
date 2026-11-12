package io.github.soham023.spottracker;

import io.github.soham023.spottracker.domain.PriceSnapshotRepository;
import io.github.soham023.spottracker.seed.SeedDataLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Default profile with network sources off: the shipped NCDEX seed fills an empty database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seedtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spot.ingest.scheduler-enabled=false", "spot.ingest.on-startup=false",
        "spot.sources.ncdex.enabled=false", "spot.sources.ncdex-product.enabled=false",
        "spot.sources.agmarknet.enabled=false"})
@AutoConfigureMockMvc
class SeedProfileIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PriceSnapshotRepository repository;

    @Autowired
    SeedDataLoader seed;

    @Test
    void seedLoadsOnceAndStatusDescribesIt() throws Exception {
        long rows = repository.count();
        assertThat(rows).isGreaterThan(4000);
        seed.run(new DefaultApplicationArguments());
        assertThat(repository.count()).as("second run is a no-op").isEqualTo(rows);

        mvc.perform(get("/api/v1/prices/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value((int) rows))
                .andExpect(jsonPath("$.firstDate").value("2022-01-03"))
                .andExpect(jsonPath("$.lastDate").value("2026-11-11"))
                .andExpect(jsonPath("$.contractMonths.length()").value(34))
                .andExpect(jsonPath("$.lastUpdated").isNotEmpty());
    }

    @Test
    void exportsCsvForARange() throws Exception {
        mvc.perform(get("/api/v1/prices/export.csv").param("from", "2026-11-09").param("to", "2026-11-09")
                        .param("type", "FUTURES").param("contract", "2026-12"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("turmeric-prices-2026-11-09-to-2026-11-09.csv")))
                .andExpect(content().string(containsString(
                        "date,commodity,source,price_type,market,variety,contract_month,price,min_price,max_price,fetched_at\n")))
                .andExpect(content().string(containsString("2026-11-09,TURMERIC,NCDEX,FUTURES,Nizamabad,,2026-12,28414.00,28050.00,28448.00,")));
        mvc.perform(get("/api/v1/prices/export.csv").param("contract", "Dec;--")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/prices").param("from", "2025-11-01").param("to", "2026-11-12").param("type", "SPOT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThan(200)));
    }
}
