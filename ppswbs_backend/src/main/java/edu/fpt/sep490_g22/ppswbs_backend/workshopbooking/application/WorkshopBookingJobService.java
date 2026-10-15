package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBookingJobService {

    private final CapacityHoldRepository holdRepository;
    private final WorkshopBookingRepository bookingRepository;
    private final WorkshopSessionRepository sessionRepository;

    private final WorkshopBookingService bookingService;

    @Scheduled(fixedDelay = 60000)
    public void scanAndReleaseExpiredHolds() {
        log.debug("Scanning for expired capacity holds...");
        List<CapacityHold> expiredHolds = holdRepository.findExpiredUnreleasedHolds(Instant.now());
        if (expiredHolds.isEmpty()) {
            return;
        }

        int processed = 0;
        for (CapacityHold hold : expiredHolds) {
            try {
                bookingService.releaseExpiredHoldInTransaction(hold.getId());
                processed++;
            } catch (Exception e) {
                log.error("Failed to release expired hold id: {}", hold.getId(), e);
            }
        }
        log.info("Released {} expired capacity holds", processed);
    }
}
