package io.github.soham023.spottracker.contract;

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
 * The "LTP" and change shown in the NCDEX product page header for one market date. The page does not
 * say which expiry it refers to, so it is stored on its own rather than as a futures price snapshot;
 * the last scrape of the day wins.
 */
@Entity
@Table(name = "product_quote", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_quote", columnNames = {"symbol", "quote_date"}))
public class ProductQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    @Column(name = "last_traded_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal lastTradedPrice;

    @Column(name = "price_change", precision = 14, scale = 2)
    private BigDecimal change;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected ProductQuote() {
    }

    public ProductQuote(String symbol, LocalDate quoteDate, BigDecimal lastTradedPrice, BigDecimal change,
                        Instant fetchedAt) {
        this.symbol = Objects.requireNonNull(symbol);
        this.quoteDate = Objects.requireNonNull(quoteDate);
        this.lastTradedPrice = Objects.requireNonNull(lastTradedPrice);
        this.change = change;
        this.fetchedAt = Objects.requireNonNull(fetchedAt);
    }

    /** Takes a later scrape's values for the same date. */
    public void refreshFrom(ProductQuote fresh) {
        this.lastTradedPrice = fresh.lastTradedPrice;
        this.change = fresh.change;
        this.fetchedAt = fresh.fetchedAt;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getQuoteDate() { return quoteDate; }
    public BigDecimal getLastTradedPrice() { return lastTradedPrice; }
    public BigDecimal getChange() { return change; }
    public Instant getFetchedAt() { return fetchedAt; }
}
