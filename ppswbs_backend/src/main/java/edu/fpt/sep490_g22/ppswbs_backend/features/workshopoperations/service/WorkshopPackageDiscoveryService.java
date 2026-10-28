package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CustomerSessionAvailabilityResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkshopPackageDiscoveryService {

    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkshopPackageRepository packageRepository;
    private final WorkshopLocationRepository locationRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopOperationsFacade operationsFacade;

    @Transactional(readOnly = true)
    public List<CustomerSessionAvailabilityResponse> discoverSessions(Long packageId, Long locationId,
                                                                       LocalDate from, LocalDate to) {
        packageRepository.findById(packageId)
                .filter(workshopPackage -> workshopPackage.getState() == WorkshopPackageState.PUBLISHED)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Package not found"));
        locationRepository.findByIdAndActiveTrue(locationId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Location not found"));

        LocalDate effectiveFrom = from != null ? from : LocalDate.now(WORKSHOP_ZONE);
        if (to != null && to.isBefore(effectiveFrom)) {
            throw new WorkshopValidationException("to must be on or after from");
        }
        List<edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession> candidates = to == null
                ? sessionRepository.findByLocationIdAndStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
                        locationId, SessionState.PUBLISHED, effectiveFrom)
                : sessionRepository.findByLocationIdAndStateAndWorkshopDateBetweenOrderByScheduledStartAtAsc(
                        locationId, SessionState.PUBLISHED, effectiveFrom, to);

        Instant now = Instant.now();
        return candidates.stream()
                .filter(session -> session.getScheduledStartAt() != null && session.getScheduledStartAt().isAfter(now))
                .map(session -> Map.entry(session, operationsFacade.getSaleEligibility(
                        new SaleEligibilityQuery(UUID.fromString(session.getReference())))))
                .filter(entry -> entry.getValue().customerSelectable())
                .map(entry -> toAvailability(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static CustomerSessionAvailabilityResponse toAvailability(
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession session,
            SaleEligibilitySnapshot eligibility) {
        return new CustomerSessionAvailabilityResponse(
                UUID.fromString(session.getReference()),
                eligibility.locationReference(),
                eligibility.scheduledStart(),
                eligibility.scheduledEnd(),
                eligibility.saleState(),
                eligibility.customerSelectable(),
                eligibility.remainingCapacity());
    }
}
