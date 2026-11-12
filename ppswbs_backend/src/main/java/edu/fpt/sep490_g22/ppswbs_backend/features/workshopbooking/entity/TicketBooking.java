package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One person's ticket inside a booking group; it owns its own folio, attendance, cancellation and change. */
@Entity
@Table(name = "ticket_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_code", nullable = false, length = 40, updatable = false)
    private String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    @Column(name = "line_index", nullable = false, updatable = false)
    private int lineIndex;

    @Column(name = "participant_ordinal", nullable = false, updatable = false)
    private int participantOrdinal;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "package_name_snapshot", nullable = false, length = 255)
    private String packageNameSnapshot;

    /** Identity Workshop Operations issued for this ticket at confirmation; Operations is the only authority. */
    @Column(name = "operations_ticket_reference", length = 36)
    private String operationsTicketReference;

    @Column(name = "qr_reference", length = 128)
    private String qrReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status", nullable = false, length = 32)
    private BookingStatus ticketStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_status", nullable = false, length = 32)
    private PaymentStatus financialStatus;

    /** Customer-requested changes already used; one is allowed per ticket (003.3 FR-044). */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "customer_changes_used", nullable = false)
    private int customerChangesUsed;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (ticketStatus == null) ticketStatus = BookingStatus.PENDING_PAYMENT;
        if (financialStatus == null) financialStatus = PaymentStatus.UNPAID;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
