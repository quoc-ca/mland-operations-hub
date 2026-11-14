package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One required session of a line, with the capacity hold Operations returned for it. */
@Entity
@Table(name = "booking_group_line_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGroupLineSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false, updatable = false)
    private BookingGroupLine line;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    @Column(name = "session_reference", nullable = false, length = 36, updatable = false)
    private String sessionReference;

    @Column(name = "location_reference", updatable = false)
    private Long locationReference;

    @Column(name = "scheduled_start", updatable = false)
    private Instant scheduledStart;

    @Column(name = "hold_reference", length = 36)
    private String holdReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_status", nullable = false, length = 16)
    private LineHoldStatus holdStatus;

    @PrePersist
    protected void onCreate() {
        if (holdStatus == null) holdStatus = LineHoldStatus.RESERVED;
    }
}
