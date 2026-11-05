package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "member_workshop_booking_quotes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_mwbq_quote_ref", columnNames = "quote_ref")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberWorkshopBookingQuote {
    public static final Duration QUOTE_TTL = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_ref", nullable = false, length = 36)
    private String quoteRef;

    @Column(name = "member_account_id", nullable = false)
    private Long memberAccountId;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "package_name_snapshot", nullable = false, columnDefinition = "LONGTEXT")
    private String packageNameSnapshot;

    @Column(name = "price_per_participant", nullable = false)
    private Long pricePerParticipant;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "session_facts_json", nullable = false, columnDefinition = "JSON")
    private String sessionFactsJson;

    @Column(name = "benefit_selection_json", nullable = false, columnDefinition = "JSON")
    private String benefitSelectionJson;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "program_discount", nullable = false)
    private Long programDiscount;

    @Column(name = "points_used", nullable = false)
    private Long pointsUsed;

    @Column(name = "point_discount", nullable = false)
    private Long pointDiscount;

    @Column(name = "post_promotion_amount", nullable = false)
    private Long postPromotionAmount;

    @Column(name = "earn_base", nullable = false)
    private Long earnBase;

    @Column(name = "terms_digest", nullable = false, length = 128)
    private String termsDigest;

    @Column(name = "quote_state", nullable = false, length = 16)
    @Builder.Default
    private String quoteState = "OPEN";

    @Column(name = "accepted_booking_code", length = 32)
    private String acceptedBookingCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (expiresAt == null) expiresAt = createdAt.plus(QUOTE_TTL);
        if (quoteState == null) quoteState = "OPEN";
    }
}
