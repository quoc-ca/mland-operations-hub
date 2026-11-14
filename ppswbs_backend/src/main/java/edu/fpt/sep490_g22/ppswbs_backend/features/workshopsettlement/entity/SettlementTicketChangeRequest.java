package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A customer change that only a Manager may allow (quota exhausted, allowance used, outside the window), waiting in
 * the Staff/Manager queue (003.3 T117, FR-045a). PENDING, then APPROVED, REJECTED or EXPIRED.
 */
@Entity
@Table(name = "settlement_ticket_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementTicketChangeRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_reference", nullable = false, unique = true, length = 64)
    private String requestReference;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "ticket_code", nullable = false, length = 64)
    private String ticketCode;

    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;

    @Column(name = "location_reference")
    private Long locationReference;

    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType;

    @Column(name = "target_session_references", nullable = false, length = 512)
    private String targetSessionReferences;

    /** Comma-separated rule codes that blocked the change (CHANGE_ALLOWANCE_EXHAUSTED, ...). */
    @Column(name = "blocked_rules", nullable = false, length = 255)
    private String blockedRules;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "price_increase_amount_vnd", nullable = false)
    @Builder.Default
    private long priceIncreaseAmountVnd = 0L;

    @Column(name = "buyer_confirmed_increase", nullable = false)
    @Builder.Default
    private boolean buyerConfirmedIncrease = false;

    @Column(name = "requester_account_id")
    private Long requesterAccountId;

    @Column(name = "requester_role", length = 32)
    private String requesterRole;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "buyer_proof", length = 128)
    private String buyerProof;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "decided_by_account_id")
    private Long decidedByAccountId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_idempotency_key", length = 128)
    private String decisionIdempotencyKey;

    @Column(name = "applied_change_reference", length = 64)
    private String appliedChangeReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
