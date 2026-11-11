package io.github.soham023.spottracker.contract;

import io.github.soham023.spottracker.source.ncdex.NcdexProductPage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Margins for one expiry on one margin date, in % of contract value. The natural key is
 * (symbol, marginDate, fileType, expiryDate), so scraping the page twice a day updates the row.
 */
@Entity
@Table(name = "daily_margin", uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_margin", columnNames = {"symbol", "margin_date", "file_type", "expiry_date"}))
public class DailyMargin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "margin_date", nullable = false)
    private LocalDate marginDate;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "initial_margin", precision = 6, scale = 2)
    private BigDecimal initialMargin;

    @Column(name = "extreme_margin", precision = 6, scale = 2)
    private BigDecimal extremeMargin;

    @Column(name = "additional_pem", precision = 6, scale = 2)
    private BigDecimal additionalPem;

    @Column(name = "total_margin", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalMargin;

    @Column(name = "additional_long", precision = 6, scale = 2)
    private BigDecimal additionalLong;

    @Column(name = "additional_short", precision = 6, scale = 2)
    private BigDecimal additionalShort;

    @Column(name = "special_long", precision = 6, scale = 2)
    private BigDecimal specialLong;

    @Column(name = "special_short", precision = 6, scale = 2)
    private BigDecimal specialShort;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected DailyMargin() {
    }

    public static DailyMargin from(String symbol, NcdexProductPage.Margin m, Instant fetchedAt) {
        DailyMargin d = new DailyMargin();
        d.symbol = Objects.requireNonNull(symbol);
        d.marginDate = Objects.requireNonNull(m.marginDate());
        d.fileType = m.fileType() == null || m.fileType().isBlank() ? "Daily" : m.fileType();
        d.expiryDate = Objects.requireNonNull(m.expiry());
        d.copy(m, fetchedAt);
        return d;
    }

    /** Copies the margin values from a fresher scrape of the same natural key. */
    public void refreshFrom(DailyMargin newer) {
        this.initialMargin = newer.initialMargin;
        this.extremeMargin = newer.extremeMargin;
        this.additionalPem = newer.additionalPem;
        this.totalMargin = newer.totalMargin;
        this.additionalLong = newer.additionalLong;
        this.additionalShort = newer.additionalShort;
        this.specialLong = newer.specialLong;
        this.specialShort = newer.specialShort;
        this.fetchedAt = newer.fetchedAt;
    }

    private void copy(NcdexProductPage.Margin m, Instant at) {
        this.initialMargin = m.initialMargin();
        this.extremeMargin = m.extremeMargin();
        this.additionalPem = m.additionalPem();
        this.totalMargin = Objects.requireNonNull(m.totalMargin());
        this.additionalLong = m.additionalLong();
        this.additionalShort = m.additionalShort();
        this.specialLong = m.specialLong();
        this.specialShort = m.specialShort();
        this.fetchedAt = Objects.requireNonNull(at);
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getMarginDate() { return marginDate; }
    public String getFileType() { return fileType; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public BigDecimal getInitialMargin() { return initialMargin; }
    public BigDecimal getExtremeMargin() { return extremeMargin; }
    public BigDecimal getAdditionalPem() { return additionalPem; }
    public BigDecimal getTotalMargin() { return totalMargin; }
    public BigDecimal getAdditionalLong() { return additionalLong; }
    public BigDecimal getAdditionalShort() { return additionalShort; }
    public BigDecimal getSpecialLong() { return specialLong; }
    public BigDecimal getSpecialShort() { return specialShort; }
    public Instant getFetchedAt() { return fetchedAt; }
}
