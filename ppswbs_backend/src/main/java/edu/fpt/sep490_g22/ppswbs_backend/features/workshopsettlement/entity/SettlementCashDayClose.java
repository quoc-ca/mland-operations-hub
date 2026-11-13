package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** One branch's cash close for one Vietnam working day (003.3 FR-036). */
@Entity
@Table(name = "settlement_cash_day_closes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementCashDayClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "close_reference", nullable = false, unique = true, length = 128, updatable = false)
    private String closeReference;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "business_day", nullable = false, updatable = false)
    private LocalDate businessDay;

    /** What the system counted: confirmed cash of the branch and day, net of change returned. */
    @Column(name = "expected_cash_vnd", nullable = false, updatable = false)
    private long expectedCashVnd;

    @Column(name = "counted_cash_vnd", nullable = false, updatable = false)
    private long countedCashVnd;

    @Column(name = "difference_vnd", nullable = false, updatable = false)
    private long differenceVnd;

    @Column(name = "difference_reason", length = 1000, updatable = false)
    private String differenceReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CashDayCloseStatus status;

    @Column(name = "closed_by_account_id", nullable = false, updatable = false)
    private Long closedByAccountId;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private Instant closedAt;

    @Column(name = "confirmed_by_account_id")
    private Long confirmedByAccountId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (closedAt == null) closedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
