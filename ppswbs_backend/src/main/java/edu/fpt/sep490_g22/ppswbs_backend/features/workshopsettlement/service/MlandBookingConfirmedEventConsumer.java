package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MlandBookingConfirmedEventConsumer {
    private final MlandBookingRefundPolicySnapshotService snapshots;

    @ApplicationModuleListener
    public void onBookingConfirmed(MlandBookingConfirmedEvent event) {
        snapshots.capture(event);
    }
}
