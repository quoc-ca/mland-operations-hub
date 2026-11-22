package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "custom_design_request_revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomDesignRequestRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "design_reference", length = 64)
    private String designReference;

    @Column(name = "design_version")
    private Integer designVersion;

    @Column(name = "image_references_json", columnDefinition = "TEXT")
    private String imageReferencesJson;

    @Column(name = "clarification_response", length = 2000)
    private String clarificationResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
