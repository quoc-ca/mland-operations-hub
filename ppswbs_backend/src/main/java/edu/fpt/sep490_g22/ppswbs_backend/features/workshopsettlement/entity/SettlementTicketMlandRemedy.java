package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Authoritative record of an Mland remedy offered and resolved for one ticket (003.3 T100, FR-043).
 */
@Entity
@Table(name = "settlement_ticket_mland_remedies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementTicketMlandRemedy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "remedy_reference", nullable = false, unique = true, length = 64)
    private String remedyReference;

    @Column(name = "ticket_code", nullable = false, length = 64)
    private String ticketCode;

    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;

    @Column(name = "cancelled_session_reference", nullable = false, length = 64)
    private String cancelledSessionReference;

    @Column(name = "replacement_session_references", nullable = false, columnDefinition = "TEXT")
    private String replacementSessionReferences;

    /** Every offered session as {@code reference|startIso|endIso} joined by ';' (T112); null on offers made before V147. */
    @Column(name = "replacement_options", columnDefinition = "TEXT")
    private String replacementOptions;

    /** The one session the buyer chose when accepting the replacement (T112). */
    @Column(name = "selected_replacement_session_reference", length = 64)
    private String selectedReplacementSessionReference;

    @Column(name = "replacement_start_at", nullable = false)
    private Instant replacementStartAt;

    @Column(name = "answer_deadline_at", nullable = false)
    private Instant answerDeadlineAt;

    @Column(name = "buyer_answer", length = 32)
    private String buyerAnswer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "refund_request_reference", length = 64)
    private String refundRequestReference;

    @Column(name = "points_returned", nullable = false)
    @Builder.Default
    private Long pointsReturned = 0L;

    @Column(name = "voucher_restored", nullable = false)
    @Builder.Default
    private boolean voucherRestored = false;

    @Column(name = "requester_account_id")
    private Long requesterAccountId;

    @Column(name = "requester_role", length = 32)
    private String requesterRole;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "buyer_proof", length = 128)
    private String buyerProof;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

