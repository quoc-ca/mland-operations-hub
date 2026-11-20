package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "design_request_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignRequestImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "design_request_id", nullable = false, length = 36)
    private String designRequestId;

    @Column(name = "image_reference", nullable = false, unique = true, length = 64)
    private String imageReference;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "quality_status", nullable = false, length = 32)
    @Builder.Default
    private String qualityStatus = "PASS";

    @Column(name = "quality_reason")
    private String qualityReason;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.qualityStatus == null) {
            this.qualityStatus = "PASS";
        }
    }
}
