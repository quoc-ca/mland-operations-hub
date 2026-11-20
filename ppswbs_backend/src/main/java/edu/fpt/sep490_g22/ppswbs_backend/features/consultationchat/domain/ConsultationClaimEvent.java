package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "consultation_claim_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationClaimEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConsultationConversation conversation;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ConsultationClaimEventType eventType;

    @Column(name = "previous_claimant_id")
    private Long previousClaimantId;

    @Column(name = "new_claimant_id")
    private Long newClaimantId;

    @Column(name = "previous_location_id")
    private Long previousLocationId;

    @Column(name = "new_location_id")
    private Long newLocationId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
