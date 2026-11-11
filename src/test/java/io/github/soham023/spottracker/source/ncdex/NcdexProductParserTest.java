package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.source.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class NcdexProductParserTest {

    private static final LocalDate DEC = LocalDate.of(2026, 12, 18);
    private static final LocalDate APR = LocalDate.of(2027, 4, 16);
    private static final LocalDate MAY = LocalDate.of(2027, 5, 20);

    private final NcdexProductPage page = new NcdexProductParser()
            .parse(Fixtures.read("ncdex-product-turmeric.html"), "TMCFGRNZM");

    @Test
    void contractCalendarIsTheUnionOfEveryExpiryOnThePage() {
        // the futures selector only lists traded contracts (Dec, Apr); margins and the matrix also list May
        assertThat(page.expiries()).containsExactly(DEC, APR, MAY);
    }

    @Test
    void dailyMarginsAreReadByColumnName() {
        assertThat(page.margins()).hasSize(3);
        assertThat(page.margins()).extracting(NcdexProductPage.Margin::expiry, m -> m.totalMargin().toPlainString(),
                        m -> m.additionalLong().toPlainString())
                .containsExactly(tuple(DEC, "17.79", "10.00"), tuple(APR, "18.47", "2.50"), tuple(MAY, "15.20", "2.50"));
        NcdexProductPage.Margin dec = page.margins().getFirst();
        assertThat(dec.fileType()).isEqualTo("Morning");
        assertThat(dec.marginDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(dec.initialMargin()).isEqualByComparingTo("14.79");
        assertThat(dec.extremeMargin()).isEqualByComparingTo("3");
        assertThat(dec.additionalPem()).isEqualByComparingTo("0");
        assertThat(dec.additionalShort()).isEqualByComparingTo("10");
        assertThat(dec.specialLong()).isEqualByComparingTo("0");
    }

    @Test
    void premiumDiscountMatrixIsSplitIntoQualityAndCentre() {
        assertThat(page.premiumDiscounts()).hasSize(16);
        assertThat(page.premiumDiscounts()).filteredOn(p -> p.expiry().equals(DEC))
                .extracting(NcdexProductPage.PremiumDiscount::quality, NcdexProductPage.PremiumDiscount::centre,
                        p -> p.premiumPct().toPlainString(), NcdexProductPage.PremiumDiscount::basis)
                .containsExactly(
                        tuple("Unpolished Fingers", "Nizamabad", "-4.00", false),
                        tuple("Farmer polished fingers Nizamabad", "Nizamabad", "0.00", true),
                        tuple("Rajapore", "Sangli", "4.00", false),
                        tuple("Erode", "Erode", "0.00", false),
                        tuple("Salem", "Erode", "2.00", false),
                        tuple("Salem", "Basmat", "0.00", false));
        // the unpolished discount is only offered on the December contract
        assertThat(page.premiumDiscounts()).filteredOn(p -> p.expiry().equals(MAY)).hasSize(5)
                .noneMatch(p -> p.quality().startsWith("Unpolished"));
        assertThat(page.premiumDiscounts()).allMatch(p -> p.basisLocation().equals("Nizamabad"));
    }

    @Test
    void headerLastTradedPriceAndChange() {
        assertThat(page.lastTradedPrice()).isEqualByComparingTo("28746");
        assertThat(page.change()).isEqualByComparingTo("332");
    }

    @Test
    void premiumFormats() {
        assertThat(NcdexProductParser.premium(" + 4%")).isEqualByComparingTo("4");
        assertThat(NcdexProductParser.premium("- 4%")).isEqualByComparingTo("-4");
        assertThat(NcdexProductParser.premium("+2.5%")).isEqualByComparingTo("2.5");
        assertThat(NcdexProductParser.premium("No Premium/Discount")).isEqualByComparingTo("0");
        assertThatThrownBy(() -> NcdexProductParser.premium("TBA")).hasMessageContaining("TBA");
    }

    @Test
    void pageWithoutAnyExpiryIsAnError() {
        assertThatThrownBy(() -> new NcdexProductParser().parse("<html><body>Maintenance</body></html>", "TMCFGRNZM"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No contract expiries");
    }

    @Test
    void datesInEveryShapeThePageUses() {
        assertThat(NcdexDates.anyDate("2026-12-18")).isEqualTo(DEC);
        assertThat(NcdexDates.anyDate("18-Dec-2026")).isEqualTo(DEC);
        assertThat(NcdexDates.anyDate("18-Dec-26")).isEqualTo(DEC);
    }
}
