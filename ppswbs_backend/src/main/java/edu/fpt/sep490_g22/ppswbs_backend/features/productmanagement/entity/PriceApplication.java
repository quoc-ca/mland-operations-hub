package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of applying one approved price (data-model {@code catalogue_price_applications},
 * FR-015/016): old and new amount, actor and time. One application per proposal and per applied sequence
 * of the variant, so an approval is applied at most once and never out of order.
 */
@Entity
@Table(name = "catalogue_price_applications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_catalogue_price_applications_sequence",
                        columnNames = {"variant_id", "applied_sequence"}),
                @UniqueConstraint(name = "uk_catalogue_price_applications_proposal", columnNames = "proposal_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(name = "applied_sequence", nullable = false)
    private long appliedSequence;

    @Column(name = "old_amount", precision = 19, scale = 0)
    private BigInteger oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 19, scale = 0)
    private BigInteger newAmount;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    public PriceApplication(Long variantId, Long proposalId, long appliedSequence, BigInteger oldAmount,
                            BigInteger newAmount, String actorId, Instant appliedAt) {
        this.variantId = Objects.requireNonNull(variantId, "variantId");
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId");
        this.appliedSequence = appliedSequence;
        this.oldAmount = oldAmount;
        this.newAmount = Objects.requireNonNull(newAmount, "newAmount");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
    }
}
