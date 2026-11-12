package io.github.soham023.spottracker.seed;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataLoaderTest {

    private static final Instant FETCHED = Instant.parse("2026-11-12T06:00:00Z");

    @Test
    void futuresSeedCoversAllExpiriesWithRealCloses() throws Exception {
        List<PriceSnapshot> rows = SeedDataLoader.readFutures(SeedDataLoader.FUTURES_CSV, FETCHED);
        assertThat(rows).hasSizeGreaterThan(4000).allMatch(r -> r.getPriceType() == PriceType.FUTURES
                && r.getSource().equals("NCDEX") && r.getMarket().equals("Nizamabad") && r.getPrice().signum() > 0);
        assertThat(rows.stream().map(PriceSnapshot::getContractMonth).distinct().count()).isEqualTo(34);
        assertThat(rows.stream().map(PriceSnapshot::getPriceDate).min(LocalDate::compareTo)).contains(LocalDate.of(2022, 1, 3));
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getContractMonth()).isEqualTo("2026-12");
            assertThat(r.getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 9));
            assertThat(r.getPrice()).isEqualByComparingTo("28414.00");
            assertThat(r.getMinPrice()).isEqualByComparingTo("28050.00");
            assertThat(r.getMaxPrice()).isEqualByComparingTo("28448.00");
        });
        long keys = rows.stream().map(r -> r.getContractMonth() + r.getPriceDate()).distinct().count();
        assertThat(keys).as("natural keys are unique").isEqualTo(rows.size());
    }

    @Test
    void spotSeedHasThreeCentresAndBhavcopyHistory() throws Exception {
        List<PriceSnapshot> rows = SeedDataLoader.readSpot(SeedDataLoader.SPOT_CSV, FETCHED);
        assertThat(rows).allMatch(r -> r.getPriceType() == PriceType.SPOT);
        assertThat(rows.stream().map(r -> r.getMarket() + "/" + r.getVariety()).distinct())
                .containsExactlyInAnyOrder("Nizamabad/Farmer Polished", "Nizamabad/Unpolished", "Sangli/Rajapore");
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getVariety()).isEqualTo("Farmer Polished");
            assertThat(r.getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(r.getPrice()).isEqualTo(new BigDecimal("24954.10"));
        });
        assertThat(rows.stream().filter(r -> r.getVariety().equals("Farmer Polished"))
                .map(PriceSnapshot::getPriceDate).min(LocalDate::compareTo)).contains(LocalDate.of(2025, 11, 10));
    }
}
