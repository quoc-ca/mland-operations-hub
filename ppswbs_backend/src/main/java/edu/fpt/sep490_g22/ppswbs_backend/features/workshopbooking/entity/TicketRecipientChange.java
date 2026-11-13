package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Audit of a recipient send or change: masked emails, actor, channel, time and correlation only (FR-041). */
@Entity
@Table(name = "ticket_recipient_changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketRecipientChange {

    public static final String KIND_FIRST_SEND = "FIRST_SEND";
    public static final String KIND_CHANGE = "CHANGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private TicketBooking ticket;

    @Column(name = "old_email_masked", length = 254)
    private String oldEmailMasked;

    @Column(name = "new_email_masked", nullable = false, length = 254)
    private String newEmailMasked;

    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;

    @Column(name = "actor_reference", length = 128)
    private String actorReference;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "change_kind", nullable = false, length = 16)
    private String changeKind;

    @Column(name = "qr_rotated", nullable = false)
    private boolean qrRotated;

    @Column(name = "counted_online", nullable = false)
    private boolean countedOnline;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "correlation_reference", length = 64)
    private String correlationReference;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) changedAt = Instant.now();
    }
}
