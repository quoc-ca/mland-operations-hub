package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_ticket_changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementTicketChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_reference", nullable = false, unique = true, length = 64)
    private String changeReference;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "ticket_code", nullable = false, length = 64)
    private String ticketCode;

    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;

    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType;

    @Column(name = "from_session_references", length = 512)
    private String fromSessionReferences;

    @Column(name = "to_session_references", length = 512)
    private String toSessionReferences;

    @Column(name = "counted", nullable = false)
    private boolean counted;

    @Column(name = "price_increase_amount_vnd", nullable = false)
    private long priceIncreaseAmountVnd;

    @Column(name = "buyer_confirmed_increase", nullable = false)
    private boolean buyerConfirmedIncrease;

    @Column(name = "applied", nullable = false)
    private boolean applied;

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

    @Column(name = "balance_due_snapshot", nullable = false)
    private long balanceDueSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

