package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ring_difficulty_preview_proofs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingDifficultyPreviewProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proof_reference_hash", nullable = false, unique = true, length = 128)
    private String proofReferenceHash;

    @Column(name = "admin_account_reference", nullable = false, length = 128)
    private String adminAccountReference;

    @Column(name = "candidate_digest", nullable = false, length = 128)
    private String candidateDigest;

    @Column(name = "expected_policy_version_id", nullable = false)
    private Long expectedPolicyVersionId;

    @Column(name = "expected_catalogue_revision", nullable = false)
    private Long expectedCatalogueRevision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
