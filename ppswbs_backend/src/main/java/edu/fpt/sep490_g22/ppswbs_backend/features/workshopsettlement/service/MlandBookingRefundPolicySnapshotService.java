package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBookingMlandRefundPolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundPolicyRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBookingMlandRefundPolicySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MlandBookingRefundPolicySnapshotService {
    public static final String DEFAULT_POLICY_REFERENCE = "MLAND-CANCELLATION-DEFAULT-100";
    private static final short DEFAULT_REFUND_PERCENT = 100;

    private final SettlementBookingMlandRefundPolicySnapshotRepository snapshots;
    private final SettlementRefundPolicyRevisionRepository revisions;

    @Transactional
    public SettlementBookingMlandRefundPolicySnapshot capture(MlandBookingConfirmedEvent event) {
        if (event == null || event.bookingReference() == null || event.bookingReference().isBlank()
                || event.confirmedAt() == null) {
            throw new IllegalArgumentException("Invalid booking confirmation event");
        }
        String bookingReference = event.bookingReference().trim();
        Instant confirmedAt = event.confirmedAt();
        var existing = snapshots.findByBookingReference(bookingReference);
        if (existing.isPresent()) {
            SettlementBookingMlandRefundPolicySnapshot prior = existing.get();
            if (confirmedAt.equals(prior.getConfirmedAt())) return prior;
            throw new IllegalStateException("Conflicting confirmation timestamp for booking policy snapshot");
        }

        var revision = revisions.findTopByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(confirmedAt);
        return snapshots.saveAndFlush(SettlementBookingMlandRefundPolicySnapshot.builder()
                .snapshotReference("MLAND-POLICY-SNAPSHOT-" + UUID.randomUUID())
                .bookingReference(bookingReference)
                .policyReference(revision.map(item -> item.getPolicyReference()).orElse(DEFAULT_POLICY_REFERENCE))
                .refundPercent(revision.map(item -> item.getWholeBookingRefundPercent().shortValue())
                        .orElse(DEFAULT_REFUND_PERCENT))
                .confirmedAt(confirmedAt)
                .build());
    }
}
