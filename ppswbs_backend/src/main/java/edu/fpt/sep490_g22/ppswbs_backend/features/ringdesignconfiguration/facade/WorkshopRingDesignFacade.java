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

    /**
     * Snapshot of the latest version of a design, only when this Member owns it and it is not archived. A missing,
     * foreign, Guest-owned or archived design all give an empty result.
     */
    Optional<BookingDesignSnapshotDto> getOwnedActiveDesignSnapshot(Long memberAccountId, String designReference);

    java.util.List<WorkshopRingDesignConsultationOption> findMemberDesignsForConsultation(Long memberAccountId);

    Optional<WorkshopRingDesignConsultationOption> validateMemberDesignForConsultation(
            Long memberAccountId, String designReference, Integer designVersion
    );
}

