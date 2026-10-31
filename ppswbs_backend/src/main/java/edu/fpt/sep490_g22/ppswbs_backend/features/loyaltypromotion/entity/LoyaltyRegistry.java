package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Singleton gate row (id = 1, created by the migration). READ-locked when a transaction is accepted and
 * WRITE-locked when a programme or the policy changes, so the candidate set and the current policy are
 * stable during accept. It also points to the current policy revision.
 */
@Entity
@Table(name = "loyalty_registry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoyaltyRegistry {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "current_policy_id", nullable = false)
    private Long currentPolicyId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Atomically moves the pointer to a new immutable policy revision (caller holds the WRITE lock). */
    public void pointTo(long policyId) {
        if (policyId <= 0) {
            throw new IllegalArgumentException("Policy id must be positive.");
        }
        this.currentPolicyId = policyId;
    }
}
