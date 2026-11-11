package io.github.soham023.spottracker.api;

import io.github.soham023.spottracker.api.dto.PageResponse;
import io.github.soham023.spottracker.api.dto.PriceDto;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.ingest.IngestionRun;
import io.github.soham023.spottracker.ingest.IngestionService;
import io.github.soham023.spottracker.ingest.SourceRunResult;
import io.github.soham023.spottracker.query.NotFoundException;
import io.github.soham023.spottracker.query.PriceQueryService;
import io.github.soham023.spottracker.spread.SpreadFlag;
import io.github.soham023.spottracker.spread.SpreadKind;
import io.github.soham023.spottracker.spread.SpreadPoint;
import io.github.soham023.spottracker.spread.SpreadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PriceController.class, IngestController.class})
class PriceControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-11T13:00:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PriceQueryService query;

    @MockitoBean
    SpreadService spreads;

    @MockitoBean
    IngestionService ingestion;

    @Test
    void pricesAreReturnedWithDefaultWindow() throws Exception {
        PriceDto dto = new PriceDto(1L, "TURMERIC", "NCDEX", "Nizamabad", null, PriceType.FUTURES, "2026-12",
                new BigDecimal("28560.00"), null, null, TODAY, Instant.parse("2026-11-11T13:00:00Z"));
        when(query.prices(eq("TURMERIC"), eq("NCDEX"), isNull(), eq(PriceType.FUTURES), eq(TODAY.minusDays(90)),
                eq(TODAY), eq(0), eq(50))).thenReturn(new PageResponse<>(List.of(dto), 0, 50, 1, 1));

        mvc.perform(get("/api/v1/prices").param("source", "NCDEX").param("type", "FUTURES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].contractMonth").value("2026-12"))
                .andExpect(jsonPath("$.content[0].price").value(28560.00))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-11"));
    }

    @Test
    void fromAfterToIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/prices").param("from", "2026-11-11").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("'from' (2026-11-11) must not be after 'to' (2026-11-01)"));
        verifyNoInteractions(query);
    }

    @Test
    void unknownPriceTypeIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/prices").param("type", "OPTIONS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parameter 'type' has an invalid value 'OPTIONS'"));
    }

    @Test
    void malformedDateIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/spreads").param("from", "11-11-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageSizeAboveLimitIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/prices").param("size", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("size")));
    }

    @Test
    void invalidCommodityIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/summary").param("commodity", "1; drop table"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCommoditySummaryIsNotFound() throws Exception {
        when(query.summary(eq("Saffron"), any())).thenThrow(new NotFoundException("No prices recorded for commodity Saffron"));
        mvc.perform(get("/api/v1/summary").param("commodity", "Saffron"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No prices recorded for commodity Saffron"));
    }

    @Test
    void spreadsCanBeFilteredToFlaggedOnes() throws Exception {
        SpreadPoint flagged = new SpreadPoint("TURMERIC", TODAY, SpreadKind.BASIS, "2026-12", new BigDecimal("24954.10"),
                LocalDate.of(2026, 11, 4), new BigDecimal("28560"), new BigDecimal("3605.90"), new BigDecimal("14.45"),
                null, Set.of(SpreadFlag.WIDE_SPREAD));
        SpreadPoint calm = new SpreadPoint("TURMERIC", TODAY, SpreadKind.MANDI, "Erode", new BigDecimal("24954.10"),
                LocalDate.of(2026, 11, 4), new BigDecimal("24350"), new BigDecimal("-604.10"), new BigDecimal("-2.42"),
                null, Set.of());
        when(spreads.spreads(eq("TURMERIC"), any(), any())).thenReturn(List.of(flagged, calm));

        mvc.perform(get("/api/v1/spreads").param("flaggedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("BASIS"))
                .andExpect(jsonPath("$[0].spreadPct").value(14.45))
                .andExpect(jsonPath("$[0].flags[0]").value("WIDE_SPREAD"));
    }

    @Test
    void alertsDefaultToLastNinetyDays() throws Exception {
        when(query.alerts(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/alerts")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        verify(query).alerts(TODAY.minusDays(90), 0, 20);
    }

    @Test
    void manualIngestReportsEachSource() throws Exception {
        when(ingestion.runAll()).thenReturn(new IngestionRun(Instant.parse("2026-11-11T13:00:00Z"),
                Instant.parse("2026-11-11T13:00:05Z"), List.of(
                new SourceRunResult("NCDEX", SourceRunResult.Status.OK, 5, 5, 0, null),
                new SourceRunResult("AGMARKNET", SourceRunResult.Status.SKIPPED, 0, 0, 0, "disabled or not configured"))));

        mvc.perform(post("/api/v1/ingest/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].status").value("OK"))
                .andExpect(jsonPath("$.sources[1].status").value("SKIPPED"));
    }
}
