package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit record for one Admin review of a mapping's readiness evidence. */
@Entity
@Table(name = "ota_readiness_assessments", uniqueConstraints =
        @UniqueConstraint(name = "uq_ota_readiness_assessment_reference", columnNames = "assessment_reference"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaReadinessAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_reference", nullable = false, length = 36, updatable = false)
    private String assessmentReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false, updatable = false)
    private OtaChannelMapping mapping;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_code", nullable = false, length = 32, updatable = false)
    private OtaProvider providerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 16, updatable = false)
    private OtaEnvironment environment;

    @Column(name = "mapping_fingerprint", nullable = false, length = 64, updatable = false)
    private String mappingFingerprint;

    @Column(name = "readiness_record_reference", nullable = false, length = 255, updatable = false)
    private String readinessRecordReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 16, updatable = false)
    private OtaReadinessAssessmentStatus decisionStatus;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "reviewer_account_reference", nullable = false, length = 128, updatable = false)
    private String reviewerAccountReference;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    private OtaReadinessAssessment(OtaChannelMapping mapping, String mappingFingerprint,
                                   String readinessRecordReference, OtaReadinessAssessmentStatus decisionStatus,
                                   String reason, String reviewerAccountReference) {
        this.assessmentReference = UUID.randomUUID().toString();
        this.mapping = mapping;
        this.providerCode = mapping.getConnection().getProviderCode();
        this.environment = mapping.getConnection().getEnvironment();
        this.mappingFingerprint = mappingFingerprint;
        this.readinessRecordReference = readinessRecordReference;
        this.decisionStatus = decisionStatus;
        this.reason = reason;
        this.reviewerAccountReference = reviewerAccountReference;
    }

    public static OtaReadinessAssessment record(OtaChannelMapping mapping, String mappingFingerprint,
                                                 String readinessRecordReference,
                                                 OtaReadinessAssessmentStatus decisionStatus, String reason,
                                                 String reviewerAccountReference) {
        if (mapping == null || mappingFingerprint == null || !mappingFingerprint.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("A mapping and its SHA-256 fingerprint are required");
        }
        if (blankOrTooLong(readinessRecordReference, 255) || decisionStatus == null
                || blankOrTooLong(reason, 500) || blankOrTooLong(reviewerAccountReference, 128)) {
            throw new IllegalArgumentException("Readiness assessment fields are invalid");
        }
        return new OtaReadinessAssessment(mapping, mappingFingerprint, readinessRecordReference.trim(),
                decisionStatus, reason.trim(), reviewerAccountReference.trim());
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) recordedAt = Instant.now();
    }

    private static boolean blankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.trim().length() > maxLength;
    }
}
