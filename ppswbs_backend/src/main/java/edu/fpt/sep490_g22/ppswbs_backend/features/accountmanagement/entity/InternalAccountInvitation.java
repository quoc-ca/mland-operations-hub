package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "internal_account_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalAccountInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "invitation_version", nullable = false)
    @Builder.Default
    private int invitationVersion = 1;

    @Column(name = "secret_digest", nullable = false, length = 64)
    private String secretDigest;

    @Column(name = "key_version", nullable = false, length = 32)
    @Builder.Default
    private String keyVersion = "v1";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "use_state", nullable = false, length = 32)
    @Builder.Default
    private String useState = "PENDING";

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "delivery_state", nullable = false, length = 32)
    @Builder.Default
    private String deliveryState = "QUEUED";

    @Column(name = "notification_request_id")
    private Long notificationRequestId;

    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;
}
