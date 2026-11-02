package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "attendance_tickets",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_at_reference", columnNames = {"reference"}),
        @UniqueConstraint(name = "uq_at_code", columnNames = {"ticket_code"}),
        @UniqueConstraint(name = "uq_at_qr", columnNames = {"qr_reference"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    @Column(name = "booking_reference", nullable = false, length = 64)
    private String bookingReference;

    @Column(name = "ticket_sequence", nullable = false)
    private Integer ticketSequence;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 32)
    private String ticketCode;

    @Column(name = "qr_reference", nullable = false, unique = true, length = 64)
    private String qrReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "validity_state", nullable = false, length = 32)
    @Builder.Default
    private TicketValidityState validityState = TicketValidityState.ACTIVE;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketSessionEntitlement> sessionEntitlements = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (reference == null) {
            reference = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
