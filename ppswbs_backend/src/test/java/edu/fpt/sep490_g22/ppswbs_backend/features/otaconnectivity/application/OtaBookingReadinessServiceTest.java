package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaBookingReadinessService.ReadinessValidationResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T112 / T113: OTA booking readiness")
class OtaBookingReadinessServiceTest {

    @Mock private OtaChannelPriceSnapshotRepository priceSnapshotRepository;

    private OtaBookingReadinessService service;
    private OtaChannelMapping mapping;
    private final Instant now = Instant.parse("2026-11-14T03:00:00Z");

    @BeforeEach
    void setUp() {
        service = new OtaBookingReadinessService(priceSnapshotRepository);
        mapping = OtaChannelMapping.builder().id(10L).status(OtaMappingStatus.BOOKING_ENABLED).build();
    }

    private OtaChannelPriceSnapshot snapshot(Instant from, Instant to) {
        return OtaChannelPriceSnapshot.providerConfirmed(mapping, 150_000L, 120_000L, 30_000L, "VND",
                "terms-1", from, to);
    }

    private void givenSnapshot(OtaChannelPriceSnapshot snapshot) {
        when(priceSnapshotRepository.findFirstByMapping_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.ofNullable(snapshot));
    }

    @Test
    @DisplayName("T112: terms whose window ended before the transaction are not active")
    void expiredTerms_areRejected() {
        givenSnapshot(snapshot(now.minusSeconds(7200), now.minusSeconds(60)));
        ReadinessValidationResult result = service.evaluateBookingReadiness(mapping, 150_000L, now);
        assertThat(result.ready()).isFalse();
        assertThat(result.rejectionReason()).isEqualTo("NO_ACTIVE_COMMERCIAL_TERMS");
    }

    @Test
    @DisplayName("T112: terms ending exactly at the transaction instant are not active")
    void termsEndingAtTheTransactionInstant_areRejected() {
        givenSnapshot(snapshot(now.minusSeconds(7200), now));
        assertThat(service.evaluateBookingReadiness(mapping, 150_000L, now).rejectionReason())
                .isEqualTo("NO_ACTIVE_COMMERCIAL_TERMS");
    }

    @Test
    @DisplayName("T112: no terms at all is rejected the same way")
    void noTerms_areRejected() {
        givenSnapshot(null);
        assertThat(service.evaluateBookingReadiness(mapping, 150_000L, now).rejectionReason())
                .isEqualTo("NO_ACTIVE_COMMERCIAL_TERMS");
    }

    @Test
    @DisplayName("T112: open-ended and still-running terms are active")
    void activeTerms_areApproved() {
        givenSnapshot(snapshot(now.minusSeconds(7200), now.plusSeconds(3600)));
        assertThat(service.evaluateBookingReadiness(mapping, 150_000L, now).ready()).isTrue();
        givenSnapshot(snapshot(now.minusSeconds(7200), null));
        assertThat(service.evaluateBookingReadiness(mapping, 150_000L, now).ready()).isTrue();
    }

    @Test
    @DisplayName("T113: an amount below Mland's retail price is accepted as reported")
    void amountBelowRetail_isAccepted() {
        givenSnapshot(snapshot(now.minusSeconds(7200), null));
        assertThat(service.evaluateBookingReadiness(mapping, 10_000L, now).ready()).isTrue();
    }

    @Test
    @DisplayName("T113: missing payment evidence still blocks the booking")
    void missingPayment_isRejected() {
        givenSnapshot(snapshot(now.minusSeconds(7200), null));
        assertThat(service.evaluateBookingReadiness(mapping, null, now).rejectionReason())
                .isEqualTo("MISSING_PAYMENT_EVIDENCE");
        assertThat(service.evaluateBookingReadiness(mapping, 0L, now).rejectionReason())
                .isEqualTo("MISSING_PAYMENT_EVIDENCE");
    }

    @Test
    @DisplayName("A mapping that is not booking-enabled is refused")
    void disabledMapping_isRejected() {
        OtaChannelMapping closed = OtaChannelMapping.builder().id(11L).status(OtaMappingStatus.SALES_CLOSED).build();
        assertThat(service.evaluateBookingReadiness(closed, 150_000L, now).rejectionReason())
                .isEqualTo("MAPPING_DISABLED");
    }
}
