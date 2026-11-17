package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Retained record of one per-ticket cancellation (003.3 T099, FR-041, FR-042).
 * Records request time, first session start, 24-hour outcome, benefits returned, actor and channel.
 */
@Entity
@Table(name = "settlement_ticket_cancellations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementTicketCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cancellation_reference", nullable = false, unique = true, length = 64)
    private String cancellationReference;

    @Column(name = "group_request_reference", nullable = false, length = 64)
    private String groupRequestReference;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 64)
    private String ticketCode;

    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;

    @Column(name = "requester_account_id")
    private Long requesterAccountId;

    @Column(name = "requester_role", nullable = false, length = 32)
    private String requesterRole;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "buyer_proof", length = 128)
    private String buyerProof;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "first_session_start", nullable = false)
    private Instant firstSessionStart;

    @Column(name = "benefit_outcome", nullable = false, length = 32)
    private String benefitOutcome;

    @Column(name = "points_returned", nullable = false)
    private long pointsReturned;

    @Column(name = "voucher_restored", nullable = false)
    private boolean voucherRestored;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

