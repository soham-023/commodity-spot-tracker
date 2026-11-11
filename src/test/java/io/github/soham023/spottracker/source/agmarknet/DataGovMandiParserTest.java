package io.github.soham023.spottracker.source.agmarknet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.source.Fixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataGovMandiParserTest {

    private static final List<String> FOCUS = List.of("Nizamabad", "Sangli", "Erode", "Hingoli", "Basmat", "Warangal");
    private final DataGovMandiParser parser = new DataGovMandiParser(new ObjectMapper());
    private final Instant now = Instant.parse("2026-11-11T13:00:00Z");

    @Test
    void keepsOnlyFocusMarketsAndUsesModalPrice() throws IOException {
        var page = parser.parse(Fixtures.read("datagov-mandi-turmeric.json"), FOCUS, now);

        assertThat(page.total()).isEqualTo(8);
        assertThat(page.snapshots()).hasSize(7)
                .extracting(PriceSnapshot::getMarket)
                .doesNotContain("Duggirala")
                .contains("Hingoli(Sant Namdev)", "Basmat");
        PriceSnapshot erode = page.snapshots().stream().filter(s -> s.getMarket().equals("Erode")).findFirst().orElseThrow();
        assertThat(erode.getCommodity()).isEqualTo("TURMERIC");
        assertThat(erode.getSource()).isEqualTo("AGMARKNET");
        assertThat(erode.getPriceType()).isEqualTo(PriceType.SPOT);
        assertThat(erode.getPrice()).isEqualByComparingTo("24350");
        assertThat(erode.getMinPrice()).isEqualByComparingTo("22600");
        assertThat(erode.getMaxPrice()).isEqualByComparingTo("26100");
        assertThat(erode.getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void acceptsPricesServedAsStrings() throws IOException {
        var sangli = parser.parse(Fixtures.read("datagov-mandi-turmeric.json"), List.of("sangli"), now).snapshots();
        assertThat(sangli).singleElement().satisfies(s -> {
            assertThat(s.getVariety()).isEqualTo("Rajapuri");
            assertThat(s.getPrice()).isEqualByComparingTo("26900");
        });
    }

    @Test
    void emptyFilterKeepsEveryMarket() throws IOException {
        assertThat(parser.parse(Fixtures.read("datagov-mandi-turmeric.json"), List.of(), now).snapshots()).hasSize(8);
    }

    @Test
    void errorStatusIsReported() {
        assertThatThrownBy(() -> parser.parse("{\"status\":\"error\",\"message\":\"Invalid API key\"}", FOCUS, now))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid API key");
    }
}
