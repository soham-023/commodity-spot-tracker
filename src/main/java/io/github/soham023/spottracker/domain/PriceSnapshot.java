package io.github.soham023.spottracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One observed price for a commodity on a given day, in ₹ per quintal.
 *
 * <p>The natural key is (source, market, variety, priceType, contractMonth, priceDate), so running
 * the same ingestion twice on a day updates the row instead of duplicating it. Spot rows use
 * {@link #NO_CONTRACT} as their contract month so the key never contains a NULL.</p>
 */
@Entity
@Table(name = "price_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uk_price_snapshot_natural",
        columnNames = {"source", "market", "variety", "price_type", "contract_month", "price_date"}))
public class PriceSnapshot {

    public static final String NO_CONTRACT = "";
    public static final String NO_VARIETY = "";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String commodity;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(nullable = false, length = 80)
    private String market;

    @Column(nullable = false, length = 80)
    private String variety = NO_VARIETY;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 10)
    private PriceType priceType;

    /** Futures contract month as yyyy-MM, or empty for spot prices. */
    @Column(name = "contract_month", nullable = false, length = 7)
    private String contractMonth = NO_CONTRACT;

    /** Representative price: polled spot, futures close, or mandi modal price (₹/quintal). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "min_price", precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected PriceSnapshot() {
    }

    public PriceSnapshot(String commodity, String source, String market, String variety, PriceType priceType,
                         String contractMonth, BigDecimal price, LocalDate priceDate, Instant fetchedAt) {
        this.commodity = Objects.requireNonNull(commodity);
        this.source = Objects.requireNonNull(source);
        this.market = Objects.requireNonNull(market);
        this.variety = variety == null ? NO_VARIETY : variety;
        this.priceType = Objects.requireNonNull(priceType);
        this.contractMonth = contractMonth == null ? NO_CONTRACT : contractMonth;
        this.price = Objects.requireNonNull(price);
        this.priceDate = Objects.requireNonNull(priceDate);
        this.fetchedAt = Objects.requireNonNull(fetchedAt);
    }

    public static PriceSnapshot spot(String commodity, String source, String market, String variety,
                                     BigDecimal price, LocalDate date, Instant fetchedAt) {
        return new PriceSnapshot(commodity, source, market, variety, PriceType.SPOT, NO_CONTRACT, price, date, fetchedAt);
    }

    public static PriceSnapshot futures(String commodity, String source, String market, String contractMonth,
                                        BigDecimal price, LocalDate date, Instant fetchedAt) {
        return new PriceSnapshot(commodity, source, market, NO_VARIETY, PriceType.FUTURES, contractMonth, price, date, fetchedAt);
    }

    public PriceSnapshot withRange(BigDecimal min, BigDecimal max) {
        this.minPrice = min;
        this.maxPrice = max;
        return this;
    }

    /** Copies the mutable price fields from a fresher observation of the same natural key. */
    public void refreshFrom(PriceSnapshot newer) {
        this.price = newer.price;
        this.minPrice = newer.minPrice;
        this.maxPrice = newer.maxPrice;
        this.fetchedAt = newer.fetchedAt;
    }

    public Long getId() { return id; }
    public String getCommodity() { return commodity; }
    public String getSource() { return source; }
    public String getMarket() { return market; }
    public String getVariety() { return variety; }
    public PriceType getPriceType() { return priceType; }
    public String getContractMonth() { return contractMonth; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public LocalDate getPriceDate() { return priceDate; }
    public Instant getFetchedAt() { return fetchedAt; }

    @Override
    public String toString() {
        return "PriceSnapshot[%s %s/%s %s %s %s %s=%s]".formatted(commodity, source, market, variety, priceType,
                contractMonth, priceDate, price);
    }
}
