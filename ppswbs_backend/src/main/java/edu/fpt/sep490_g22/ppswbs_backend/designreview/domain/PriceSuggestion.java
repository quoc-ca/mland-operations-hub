package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "price_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false, length = 36)
    private String proposalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private TargetType targetType;

    @Column(name = "advisory", nullable = false)
    @Builder.Default
    private boolean advisory = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_status", nullable = false, length = 32)
    private SuggestionStatus suggestionStatus;

    @Column(name = "suggested_price")
    private Double suggestedPrice;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "provider_correlation_id", length = 128)
    private String providerCorrelationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_decision", length = 32)
    private ManagerDecision managerDecision;

    @Column(name = "final_price")
    private Double finalPrice;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.advisory = true;
    }
}
