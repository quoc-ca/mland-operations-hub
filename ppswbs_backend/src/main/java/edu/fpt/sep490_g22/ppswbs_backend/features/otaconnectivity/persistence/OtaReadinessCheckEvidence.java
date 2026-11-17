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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One immutable checklist outcome and safe external evidence reference. */
@Entity
@Table(name = "ota_readiness_check_evidence", uniqueConstraints =
        @UniqueConstraint(name = "uq_ota_readiness_check", columnNames = {"assessment_id", "check_code"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaReadinessCheckEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false)
    private OtaReadinessAssessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_code", nullable = false, length = 48, updatable = false)
    private OtaReadinessCheck checkCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16, updatable = false)
    private OtaReadinessCheckOutcome outcome;

    @Column(name = "evidence_reference", length = 255, updatable = false)
    private String evidenceReference;

    private OtaReadinessCheckEvidence(OtaReadinessAssessment assessment, OtaReadinessCheck checkCode,
                                     OtaReadinessCheckOutcome outcome, String evidenceReference) {
        this.assessment = assessment;
        this.checkCode = checkCode;
        this.outcome = outcome;
        this.evidenceReference = evidenceReference;
    }

    public static OtaReadinessCheckEvidence record(OtaReadinessAssessment assessment,
                                                    OtaReadinessCheck checkCode,
                                                    OtaReadinessCheckOutcome outcome,
                                                    String evidenceReference) {
        if (assessment == null || checkCode == null || outcome == null) {
            throw new IllegalArgumentException("Assessment, check and outcome are required");
        }
        String normalizedReference = evidenceReference == null ? null : evidenceReference.trim();
        if (outcome == OtaReadinessCheckOutcome.VERIFIED
                && (normalizedReference == null || normalizedReference.isBlank())) {
            throw new IllegalArgumentException("Verified readiness checks require an evidence reference");
        }
        if (normalizedReference != null && (normalizedReference.isBlank() || normalizedReference.length() > 255)) {
            throw new IllegalArgumentException("Readiness evidence reference is invalid");
        }
        return new OtaReadinessCheckEvidence(assessment, checkCode, outcome, normalizedReference);
    }
}
