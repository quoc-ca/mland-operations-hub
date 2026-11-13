package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One cart line of a booking group: a package, its required sessions and the number of people on it. */
@Entity
@Table(name = "booking_group_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGroupLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    @Column(name = "line_index", nullable = false, updatable = false)
    private int lineIndex;

    /** Reference Operations holds capacity against, so T093 can commit or release this line's holds. */
    @Column(name = "line_reference", nullable = false, length = 48, updatable = false)
    private String lineReference;

    @Column(name = "package_id", nullable = false, updatable = false)
    private Long packageId;

    @Column(name = "package_name_snapshot", nullable = false, length = 255, updatable = false)
    private String packageNameSnapshot;

    @Column(name = "price_per_participant_vnd", nullable = false, updatable = false)
    private long pricePerParticipantVnd;

    @Column(name = "ticket_count", nullable = false, updatable = false)
    private int ticketCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal ASC")
    @Builder.Default
    private List<BookingGroupLineSession> sessions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public void addSession(BookingGroupLineSession session) {
        session.setLine(this);
        sessions.add(session);
    }
}
