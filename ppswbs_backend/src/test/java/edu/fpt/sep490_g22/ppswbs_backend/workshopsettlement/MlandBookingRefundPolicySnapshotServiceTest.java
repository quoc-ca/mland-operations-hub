package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBookingMlandRefundPolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundPolicyRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBookingMlandRefundPolicySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicyRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.MlandBookingRefundPolicySnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MlandBookingRefundPolicySnapshotServiceTest {
    private SettlementBookingMlandRefundPolicySnapshotRepository snapshots;
    private SettlementRefundPolicyRevisionRepository revisions;
    private MlandBookingRefundPolicySnapshotService service;

    @BeforeEach
    void setUp() {
        snapshots = mock(SettlementBookingMlandRefundPolicySnapshotRepository.class);
        revisions = mock(SettlementRefundPolicyRevisionRepository.class);
        service = new MlandBookingRefundPolicySnapshotService(snapshots, revisions);
        when(snapshots.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void capturesInitialDefaultWhenNoRevisionWasEffectiveAtConfirmation() {
        Instant confirmedAt = Instant.parse("2026-10-31T10:00:00Z");
        when(revisions.findTopByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(confirmedAt))
                .thenReturn(Optional.empty());

        var snapshot = service.capture(new MlandBookingConfirmedEvent("BK-DEFAULT", confirmedAt));

        assertThat(snapshot.getPolicyReference()).isEqualTo("MLAND-CANCELLATION-DEFAULT-100");
        assertThat(snapshot.getRefundPercent()).isEqualTo((short) 100);
        assertThat(snapshot.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void choosesLatestRevisionEffectiveAtConfirmationAndIgnoresLaterRevision() {
        Instant confirmedAt = Instant.parse("2026-10-31T10:00:00Z");
        var effective = SettlementRefundPolicyRevision.builder()
                .policyReference("POLICY-EFFECTIVE").effectiveAt(confirmedAt.minusSeconds(60))
                .wholeBookingRefundPercent(65).build();
        when(revisions.findTopByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(confirmedAt))
                .thenReturn(Optional.of(effective));

        var snapshot = service.capture(new MlandBookingConfirmedEvent("BK-REVISED", confirmedAt));

        assertThat(snapshot.getPolicyReference()).isEqualTo("POLICY-EFFECTIVE");
        assertThat(snapshot.getRefundPercent()).isEqualTo((short) 65);
    }

    @Test
    void sameConfirmationReplayIsIdempotentButConflictingTimestampFailsClosed() {
        Instant confirmedAt = Instant.parse("2026-10-31T10:00:00Z");
        var existing = SettlementBookingMlandRefundPolicySnapshot.builder()
                .bookingReference("BK-REPLAY").policyReference("POLICY-1")
                .refundPercent((short) 50).confirmedAt(confirmedAt).build();
        when(snapshots.findByBookingReference("BK-REPLAY")).thenReturn(Optional.of(existing));

        assertThat(service.capture(new MlandBookingConfirmedEvent("BK-REPLAY", confirmedAt))).isSameAs(existing);
        assertThatThrownBy(() -> service.capture(new MlandBookingConfirmedEvent(
                "BK-REPLAY", confirmedAt.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class);
        verify(snapshots, never()).saveAndFlush(any());
    }
}
