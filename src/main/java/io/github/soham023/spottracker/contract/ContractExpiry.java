package io.github.soham023.spottracker.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One futures expiry seen on the NCDEX product page. The page only lists active contracts, so the
 * calendar grows over time; {@code lastSeen} tells when a contract dropped off the page.
 */
@Entity
@Table(name = "contract_expiry", uniqueConstraints = @UniqueConstraint(
        name = "uk_contract_expiry", columnNames = {"symbol", "expiry_date"}))
public class ContractExpiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "first_seen", nullable = false)
    private LocalDate firstSeen;

    @Column(name = "last_seen", nullable = false)
    private LocalDate lastSeen;

    protected ContractExpiry() {
    }

    public ContractExpiry(String symbol, LocalDate expiryDate, LocalDate seenOn) {
        this.symbol = Objects.requireNonNull(symbol);
        this.expiryDate = Objects.requireNonNull(expiryDate);
        this.firstSeen = Objects.requireNonNull(seenOn);
        this.lastSeen = seenOn;
    }

    /** Returns true when this changed the row. */
    public boolean seen(LocalDate on) {
        if (on.isAfter(lastSeen)) {
            lastSeen = on;
            return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public LocalDate getFirstSeen() { return firstSeen; }
    public LocalDate getLastSeen() { return lastSeen; }
}
