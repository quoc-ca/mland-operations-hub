package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_tickets", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bt_ticket_code", columnNames = {"ticket_code"}),
    @UniqueConstraint(name = "uq_bt_qr_reference", columnNames = {"qr_reference"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @Column(name = "ticket_code", nullable = false, length = 64)
    private String ticketCode;

    @Column(name = "qr_reference", nullable = false, length = 128)
    private String qrReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 32)
    private TicketDeliveryStatus deliveryStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (deliveryStatus == null) {
            deliveryStatus = TicketDeliveryStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
