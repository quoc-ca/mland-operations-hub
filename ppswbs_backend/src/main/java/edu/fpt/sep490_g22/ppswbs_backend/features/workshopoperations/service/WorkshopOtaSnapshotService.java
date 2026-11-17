package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionOtaSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSessionOtaSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOtaResyncFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshotChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkshopOtaSnapshotService implements WorkshopOtaResyncFacade {
    private final WorkshopSessionOtaSnapshotRepository snapshotRepository;
    private final CapacityHoldRepository holdRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot publishForObservableChange(WorkshopSession session,
                                                                  String changeReference,
                                                                  Instant effectiveAt) {
        if (session.getState() == SessionState.DRAFT) return null;
        WorkshopSessionOtaSnapshot last = snapshotRepository
                .findTopBySessionReferenceOrderBySourceVersionDesc(session.getReference())
                .orElse(null);
        long nextVersion = last == null ? 1L : last.getSourceVersion() + 1L;
        int activeHolds = holdRepository.sumActiveHoldsForSession(session.getId(), effectiveAt);
        int confirmedSeats = entitlementRepository.countActiveConfirmedSeatsForSession(session.getId());
        int availability = Math.max(0, session.getCapacity() - activeHolds - confirmedSeats);
        String locationReference = String.valueOf(session.getLocation().getId());
        String saleState = switch (session.getState()) {
            case PUBLISHED -> "PUBLISHED";
            case SALES_PAUSED -> "SALES_PAUSED";
            case CANCELLATION_REQUESTED, CANCELLED, ATTENDANCE_FINALIZED, IN_PROGRESS -> "CLOSED";
            default -> "CLOSED";
        };
        if (last != null
                && Objects.equals(last.getLocationReference(), locationReference)
                && Objects.equals(last.getScheduledStart(), session.getScheduledStartAt())
                && Objects.equals(last.getScheduledEnd(), session.getScheduledEndAt())
                && Objects.equals(last.getSellableAvailability(), availability)
                && Objects.equals(last.getSaleState(), saleState)) {
            return toFacade(last);
        }
        WorkshopSessionOtaSnapshot entity = WorkshopSessionOtaSnapshot.builder()
                .sessionReference(session.getReference())
                .locationReference(locationReference)
                .scheduledStart(session.getScheduledStartAt())
                .scheduledEnd(session.getScheduledEndAt())
                .sellableAvailability(availability)
                .saleState(saleState)
                .sourceVersion(nextVersion)
                .changeReference(changeReference)
                .effectiveAt(effectiveAt)
                .build();
        WorkshopSessionOtaSnapshot saved = snapshotRepository.save(entity);
        eventPublisher.publishEvent(new WorkshopSessionOtaSnapshotChanged(toFacade(saved)));
        return toFacade(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot resync(String sessionReference) {
        return snapshotRepository.findTopBySessionReferenceOrderBySourceVersionDesc(sessionReference)
                .map(this::toFacade)
                .orElseThrow(() -> new WorkshopNotFoundException("OTA snapshot not found for session " + sessionReference));
    }

    private edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot toFacade(WorkshopSessionOtaSnapshot s) {
        return new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot(
                s.getSessionReference(), s.getLocationReference(), s.getScheduledStart(), s.getScheduledEnd(),
                s.getSellableAvailability(), s.getSaleState(), s.getSourceVersion(), s.getChangeReference(), s.getEffectiveAt());
    }
}
