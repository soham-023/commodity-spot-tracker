package io.github.soham023.spottracker.alert;

import io.github.soham023.spottracker.spread.SpreadFlag;
import io.github.soham023.spottracker.spread.SpreadKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A spread flag that was raised; {@code dedupeKey} guarantees one alert per flag per day. */
@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String commodity;

    @Column(name = "alert_date", nullable = false)
    private LocalDate alertDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SpreadKind kind;

    @Column(nullable = false, length = 80)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpreadFlag flag;

    @Column(name = "spread_pct", precision = 8, scale = 2)
    private BigDecimal spreadPct;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "dedupe_key", nullable = false, length = 200, unique = true)
    private String dedupeKey;

    @Column(name = "delivered_to", nullable = false, length = 200)
    private String deliveredTo = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Alert() {
    }

    public Alert(String commodity, LocalDate alertDate, SpreadKind kind, String label, SpreadFlag flag,
                 BigDecimal spreadPct, String message, Instant createdAt) {
        this.commodity = commodity;
        this.alertDate = alertDate;
        this.kind = kind;
        this.label = label;
        this.flag = flag;
        this.spreadPct = spreadPct;
        this.message = message;
        this.createdAt = createdAt;
        this.dedupeKey = dedupeKey(commodity, alertDate, kind, label, flag);
    }

    public static String dedupeKey(String commodity, LocalDate date, SpreadKind kind, String label, SpreadFlag flag) {
        return String.join("|", commodity, date.toString(), kind.name(), label, flag.name());
    }

    public void markDelivered(String channels) {
        this.deliveredTo = channels;
    }

    public Long getId() { return id; }
    public String getCommodity() { return commodity; }
    public LocalDate getAlertDate() { return alertDate; }
    public SpreadKind getKind() { return kind; }
    public String getLabel() { return label; }
    public SpreadFlag getFlag() { return flag; }
    public BigDecimal getSpreadPct() { return spreadPct; }
    public String getMessage() { return message; }
    public String getDedupeKey() { return dedupeKey; }
    public String getDeliveredTo() { return deliveredTo; }
    public Instant getCreatedAt() { return createdAt; }
}
