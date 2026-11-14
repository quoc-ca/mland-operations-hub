package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.BookingDesignSnapshotDto;

import java.util.Optional;

public interface WorkshopRingDesignFacade {

    BookingDesignSnapshotDto attachAndLockDesignForBooking(
            String designId,
            String bookingReference,
            String packageReference,
            String branchReference,
            Long memberAccountId,
            String guestSessionReference
    );

    void unlockDesignOnBookingRollback(String designId);

    void finalizeLock(String designId);

    Optional<BookingDesignSnapshotDto> getDesignSnapshot(String designId);
}

