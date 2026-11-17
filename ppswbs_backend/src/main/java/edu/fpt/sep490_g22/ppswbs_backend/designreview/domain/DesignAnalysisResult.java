package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "design_analysis_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "design_request_id", nullable = false, unique = true, length = 36)
    private String designRequestId;

    @Column(name = "schema_version", nullable = false, length = 32)
    @Builder.Default
    private String schemaVersion = "v1.0";

    @Column(name = "image_quality_json", columnDefinition = "TEXT")
    private String imageQualityJson;

    @Column(name = "candidate_features_json", columnDefinition = "TEXT")
    private String candidateFeaturesJson;

    @Column(name = "confidence_json", columnDefinition = "TEXT")
    private String confidenceJson;

    @Column(name = "safe_explanation", columnDefinition = "TEXT")
    private String safeExplanation;

    @Column(name = "provider_correlation_id", length = 128)
    private String providerCorrelationId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
        if (this.schemaVersion == null) {
            this.schemaVersion = "v1.0";
        }
    }
}
