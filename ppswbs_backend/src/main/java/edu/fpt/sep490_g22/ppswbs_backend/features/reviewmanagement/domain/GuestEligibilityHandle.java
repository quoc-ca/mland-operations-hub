package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "guest_eligibility_handles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_geh_hash", columnNames = {"handle_hash"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestEligibilityHandle {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "handle_hash", length = 64, nullable = false)
    private String handleHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", length = 16, nullable = false)
    private ContactType contactType;

    @Column(name = "contact_value_masked", length = 254, nullable = false)
    private String contactValueMasked;

    @Column(name = "source_references_json", columnDefinition = "TEXT", nullable = false)
    private String sourceReferencesJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}

