package edu.fpt.sep490_g22.ppswbs_backend.otaconnectivity;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCommercialStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtaChannelPriceSnapshotTest {

    @Test
    void confirmedTermsCreateAnApprovedImmutableSnapshotWithTheEvidenceReference() {
        OtaChannelMapping mapping = OtaChannelMapping.builder().build();
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        OtaChannelPriceSnapshot snapshot = OtaChannelPriceSnapshot.providerConfirmed(
                mapping, 500_000L, 420_000L, 80_000L, "VND",
                "contract-ref-2026-11", from, null);

        assertThat(snapshot.getMapping()).isSameAs(mapping);
        assertThat(snapshot.getRetailAmountMinor()).isEqualTo(500_000L);
        assertThat(snapshot.getTargetNetAmountMinor()).isEqualTo(420_000L);
        assertThat(snapshot.getCommissionAmountMinor()).isEqualTo(80_000L);
        assertThat(snapshot.getCurrencyCode()).isEqualTo("VND");
        assertThat(snapshot.getTermsReference()).isEqualTo("contract-ref-2026-11");
        assertThat(OtaCommercialStatus.fromConfirmedSnapshot(false))
                .isEqualTo(OtaCommercialStatus.PENDING_COMMERCIAL_CONFIRMATION);
        assertThat(OtaCommercialStatus.fromConfirmedSnapshot(true)).isEqualTo(OtaCommercialStatus.APPROVED);
    }

    @Test
    void invalidOrUnconfirmedTermsCannotCreateASnapshot() {
        OtaChannelMapping mapping = OtaChannelMapping.builder().build();
        Instant from = Instant.parse("2026-11-01T00:00:00Z");

        assertThatThrownBy(() -> OtaChannelPriceSnapshot.providerConfirmed(
                mapping, 100L, -1L, null, "VND", "contract-ref", from, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtaChannelPriceSnapshot.providerConfirmed(
                mapping, 100L, 90L, null, "VND", " ", from, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtaChannelPriceSnapshot.providerConfirmed(
                mapping, 100L, 90L, null, "VND", "contract-ref", from, from))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
