package io.github.soham023.spottracker.contract;

import io.github.soham023.spottracker.ingest.SnapshotWriter;
import io.github.soham023.spottracker.source.ncdex.NcdexProductPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Upserts one scrape of the product page by natural key (expiry; margin date + file + expiry;
 * expiry + location + as-of date), so re-running the scrape on the same day is idempotent.
 */
@Service
public class ContractDataWriter {

    private final ContractExpiryRepository expiries;
    private final DailyMarginRepository margins;
    private final PremiumDiscountRepository matrix;

    public ContractDataWriter(ContractExpiryRepository expiries, DailyMarginRepository margins,
                              PremiumDiscountRepository matrix) {
        this.expiries = expiries;
        this.margins = margins;
        this.matrix = matrix;
    }

    /**
     * @param today     market date of the scrape; also the matrix as-of date when the page carries no margin date
     */
    @Transactional
    public SnapshotWriter.Counts upsert(NcdexProductPage page, LocalDate today, Instant fetchedAt) {
        int inserted = 0;
        int updated = 0;
        String symbol = page.symbol();
        for (LocalDate expiry : page.expiries()) {
            var existing = expiries.findBySymbolAndExpiryDate(symbol, expiry);
            if (existing.isPresent()) {
                updated += existing.get().seen(today) ? 1 : 0;
            } else {
                expiries.save(new ContractExpiry(symbol, expiry, today));
                inserted++;
            }
        }
        for (NcdexProductPage.Margin m : page.margins()) {
            DailyMargin fresh = DailyMargin.from(symbol, m, fetchedAt);
            var existing = margins.findBySymbolAndMarginDateAndFileTypeAndExpiryDate(symbol, fresh.getMarginDate(),
                    fresh.getFileType(), fresh.getExpiryDate());
            if (existing.isPresent()) {
                existing.get().refreshFrom(fresh);
                updated++;
            } else {
                margins.save(fresh);
                inserted++;
            }
        }
        LocalDate asOf = page.margins().stream().map(NcdexProductPage.Margin::marginDate)
                .max(LocalDate::compareTo).orElse(today);
        for (NcdexProductPage.PremiumDiscount p : page.premiumDiscounts()) {
            PremiumDiscount fresh = PremiumDiscount.from(symbol, p, asOf, fetchedAt);
            var existing = matrix.findBySymbolAndExpiryDateAndLocationAndAsOfDate(symbol, p.expiry(), p.location(), asOf);
            if (existing.isPresent()) {
                existing.get().refreshFrom(fresh);
                updated++;
            } else {
                matrix.save(fresh);
                inserted++;
            }
        }
        return new SnapshotWriter.Counts(inserted, updated);
    }
}
