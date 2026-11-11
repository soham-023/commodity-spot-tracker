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
 * One row of the NCDEX premium/discount matrix: what a delivery of {@code quality} at {@code centre}
 * is worth relative to the basis quality, for one expiry, as published on {@code asOfDate}.
 */
@Entity
@Table(name = "premium_discount", uniqueConstraints = @UniqueConstraint(
        name = "uk_premium_discount", columnNames = {"symbol", "expiry_date", "location", "as_of_date"}))
public class PremiumDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "basis_location", nullable = false, length = 80)
    private String basisLocation;

    @Column(nullable = false, length = 160)
    private String location;

    @Column(nullable = false, length = 120)
    private String quality;

    @Column(nullable = false, length = 80)
    private String centre;

    /** + premium / − discount over the basis quality, in %. */
    @Column(name = "premium_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal premiumPct;

    @Column(name = "is_basis", nullable = false)
    private boolean basis;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected PremiumDiscount() {
    }

    public PremiumDiscount(String symbol, LocalDate expiryDate, LocalDate asOfDate, String basisLocation, String location,
                           String quality, String centre, BigDecimal premiumPct, boolean basis, Instant fetchedAt) {
        this.symbol = Objects.requireNonNull(symbol);
        this.expiryDate = Objects.requireNonNull(expiryDate);
        this.asOfDate = Objects.requireNonNull(asOfDate);
        this.basisLocation = Objects.requireNonNull(basisLocation);
        this.location = Objects.requireNonNull(location);
        this.quality = Objects.requireNonNull(quality);
        this.centre = Objects.requireNonNull(centre);
        this.premiumPct = Objects.requireNonNull(premiumPct);
        this.basis = basis;
        this.fetchedAt = Objects.requireNonNull(fetchedAt);
    }

    public static PremiumDiscount from(String symbol, NcdexProductPage.PremiumDiscount p, LocalDate asOf, Instant fetchedAt) {
        return new PremiumDiscount(symbol, p.expiry(), asOf, p.basisLocation(), p.location(), p.quality(), p.centre(),
                p.premiumPct(), p.basis(), fetchedAt);
    }

    public void refreshFrom(PremiumDiscount newer) {
        this.basisLocation = newer.basisLocation;
        this.quality = newer.quality;
        this.centre = newer.centre;
        this.premiumPct = newer.premiumPct;
        this.basis = newer.basis;
        this.fetchedAt = newer.fetchedAt;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public String getBasisLocation() { return basisLocation; }
    public String getLocation() { return location; }
    public String getQuality() { return quality; }
    public String getCentre() { return centre; }
    public BigDecimal getPremiumPct() { return premiumPct; }
    public boolean isBasis() { return basis; }
    public Instant getFetchedAt() { return fetchedAt; }
}
