package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.InternalProvisioningStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "internal_account_provisionings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalAccountProvisioning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "request_digest", length = 64)
    private String requestDigest;

    @Column(name = "intended_email", length = 320)
    private String intendedEmail;

    @Column(name = "intended_role", length = 32)
    private String intendedRole;

    @Column(name = "intended_locations", columnDefinition = "JSON")
    private String intendedLocations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private InternalProvisioningStatus status = InternalProvisioningStatus.INVITATION_SENT;

    @Column(name = "invitation_last_sent_at")
    private Instant invitationLastSentAt;

    @Column(name = "created_by_admin_id", nullable = false)
    private Long createdByAdminId;

    @Column(name = "firebase_setup_link_issued_at")
    private Instant firebaseSetupLinkIssuedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
