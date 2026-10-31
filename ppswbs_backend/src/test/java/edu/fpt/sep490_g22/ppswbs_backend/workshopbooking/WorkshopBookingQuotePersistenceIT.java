package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MemberWorkshopBookingQuote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class WorkshopBookingQuotePersistenceIT {

    @Autowired
    private MemberWorkshopBookingQuoteRepository quotes;

    @Test
    void persistsAndLoadsQuoteExpiryAndBenefitSnapshotsOnMySql() {
        Instant createdAt = Instant.parse("2026-10-29T12:00:00Z");
        Instant expiresAt = createdAt.plus(MemberWorkshopBookingQuote.QUOTE_TTL);
        String quoteRef = UUID.randomUUID().toString();

        MemberWorkshopBookingQuote saved = quotes.saveAndFlush(MemberWorkshopBookingQuote.builder()
                .quoteRef(quoteRef)
                .memberAccountId(900001L)
                .packageId(900002L)
                .packageNameSnapshot("Persistence fixture")
                .pricePerParticipant(450_000L)
                .participantCount(2)
                .sessionFactsJson("[{\"sessionReference\":\"" + UUID.randomUUID() + "\"}]")
                .benefitSelectionJson("{\"choice\":\"NONE\",\"requestedPoints\":\"0\"}")
                .grossAmount(900_000L)
                .programDiscount(0L)
                .pointsUsed(0L)
                .pointDiscount(0L)
                .postPromotionAmount(900_000L)
                .earnBase(900_000L)
                .termsDigest("terms-persistence-test")
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build());

        MemberWorkshopBookingQuote loaded = quotes.findByQuoteRefAndMemberAccountId(quoteRef, 900001L)
                .orElseThrow();

        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(loaded.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(loaded.getSessionFactsJson()).contains("sessionReference");
        assertThat(loaded.getBenefitSelectionJson()).contains("requestedPoints");
        assertThat(loaded.getTermsDigest()).isEqualTo("terms-persistence-test");
    }
}
