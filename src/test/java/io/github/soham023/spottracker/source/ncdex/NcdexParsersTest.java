package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.domain.PriceSnapshot;
import io.github.soham023.spottracker.domain.PriceType;
import io.github.soham023.spottracker.source.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the parsers against pages recorded from ncdex.com on 2026-11-11. */
class NcdexParsersTest {

    private static final Instant FETCHED = Instant.parse("2026-11-11T13:00:00Z");

    @Test
    void parsesPolledTurmericSpotPrices() {
        List<PriceSnapshot> spots = new NcdexSpotParser().parse(Fixtures.read("ncdex-live-spot-turmeric.html"), FETCHED);

        assertThat(spots).hasSize(3).allSatisfy(s -> {
            assertThat(s.getCommodity()).isEqualTo("TURMERIC");
            assertThat(s.getSource()).isEqualTo("NCDEX");
            assertThat(s.getPriceType()).isEqualTo(PriceType.SPOT);
            assertThat(s.getContractMonth()).isEmpty();
        });
        assertThat(spots).extracting(PriceSnapshot::getVariety, PriceSnapshot::getMarket, PriceSnapshot::getPrice,
                        PriceSnapshot::getPriceDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Farmer Polished", "Nizamabad", new BigDecimal("24954.10"), LocalDate.of(2026, 11, 4)),
                        org.assertj.core.groups.Tuple.tuple("Unpolished", "Nizamabad", new BigDecimal("23698.55"), LocalDate.of(2026, 11, 4)),
                        org.assertj.core.groups.Tuple.tuple("Rajapore", "Sangli", new BigDecimal("28650.00"), LocalDate.of(2026, 11, 6)));
    }

    @Test
    void parsesTurmericFuturesContractsOnly() {
        String html = Fixtures.read("ncdex-live-quotes.html");
        List<PriceSnapshot> futures = new NcdexFuturesParser().parse(html, "TMCFGRNZM", "TURMERIC", "Nizamabad", FETCHED);

        assertThat(futures).hasSize(2).allSatisfy(f -> {
            assertThat(f.getPriceType()).isEqualTo(PriceType.FUTURES);
            assertThat(f.getPriceDate()).isEqualTo(LocalDate.of(2026, 11, 11));
            assertThat(f.getMarket()).isEqualTo("Nizamabad");
        });
        PriceSnapshot dec = futures.get(0);
        assertThat(dec.getContractMonth()).isEqualTo("2026-12");
        assertThat(dec.getPrice()).isEqualByComparingTo("28560");
        assertThat(dec.getMinPrice()).isEqualByComparingTo("28424");
        assertThat(dec.getMaxPrice()).isEqualByComparingTo("28900");
        assertThat(futures.get(1).getContractMonth()).isEqualTo("2027-04");
        assertThat(futures.get(1).getPrice()).isEqualByComparingTo("28210");
    }

    @Test
    void futuresRowAlsoExposesPreviousCloseAndExchangeSpot() {
        var quotes = new NcdexFuturesParser().parseQuotes(Fixtures.read("ncdex-live-quotes.html"), "TMCFGRNZM");

        assertThat(quotes.get(0).previousClose()).isEqualByComparingTo("28414");
        assertThat(quotes.get(0).exchangeSpot()).isEqualByComparingTo("24954.10");
    }

    @Test
    void otherSymbolsAreIgnored() {
        var quotes = new NcdexFuturesParser().parseQuotes(Fixtures.read("ncdex-live-quotes.html"), "JEERAUNJHA");
        assertThat(quotes).isNotEmpty().allSatisfy(q -> assertThat(q.symbol()).isEqualTo("JEERAUNJHA"));
    }

    @Test
    void pageWithoutTimestampIsRejected() {
        assertThatThrownBy(() -> new NcdexFuturesParser().parse("<html><body>maintenance</body></html>",
                "TMCFGRNZM", "TURMERIC", "Nizamabad", FETCHED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
