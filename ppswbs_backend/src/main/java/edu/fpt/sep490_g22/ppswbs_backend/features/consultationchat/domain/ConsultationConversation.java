package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "consultation_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_reference", nullable = false, unique = true, length = 64)
    private String conversationReference;

    @Column(name = "member_user_id", nullable = false)
    private Long memberUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 32)
    private ConsultationContextType contextType;

    @Column(name = "context_reference", nullable = false, length = 64)
    private String contextReference;

    @Column(name = "context_version", nullable = false)
    private Integer contextVersion;

    @Column(name = "context_snapshot", columnDefinition = "JSON", nullable = false)
    private String contextSnapshot;

    @Column(name = "assigned_location_id")
    private Long assignedLocationId;

    @Column(name = "claimed_by_user_id")
    private Long claimedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ConsultationStatus status = ConsultationStatus.OPEN;

    @Column(name = "aggregate_version", nullable = false)
    @Builder.Default
    private Long aggregateVersion = 1L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
