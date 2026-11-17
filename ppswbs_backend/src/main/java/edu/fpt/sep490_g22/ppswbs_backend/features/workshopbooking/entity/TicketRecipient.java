package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** The optional recipient of one ticket (003.1 FR-041). Read projections only expose the masked email. */
@Entity
@Table(name = "ticket_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private TicketBooking ticket;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    /** Online changes counted against the limit of 3; Staff changes and the first send are not counted. */
    @Column(name = "change_count", nullable = false)
    private int changeCount;

    @Column(name = "qr_generation", nullable = false)
    private int qrGeneration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (qrGeneration < 1) qrGeneration = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
