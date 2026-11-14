package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "notification_template_version",
    uniqueConstraints = @UniqueConstraint(name = "uq_ntv_def_version", columnNames = {"template_definition_id", "version_number"})
)
@Getter
@Setter
@NoArgsConstructor
public class TemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_definition_id", nullable = false)
    private TemplateDefinition templateDefinition;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "subject_template", nullable = false, length = 255)
    private String subjectTemplate;

    @Column(name = "html_template", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String htmlTemplate;

    @Column(name = "plain_text_template", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String plainTextTemplate;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "activated_by", length = 64)
    private String activatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

