package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CustomerSessionAvailabilityResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
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
import java.util.AbstractMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkshopPackageDiscoveryService {

    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkshopPackageRepository packageRepository;
    private final WorkshopLocationRepository locationRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopOperationsFacade operationsFacade;
    private final WorkshopPackageAvailabilityService availabilityService;

    @Transactional(readOnly = true)
    public List<CustomerSessionAvailabilityResponse> discoverSessions(Long packageId, Long locationId,
                                                                       LocalDate from, LocalDate to) {
        packageRepository.findById(packageId)
                .filter(workshopPackage -> workshopPackage.getState() == WorkshopPackageState.PUBLISHED)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Package not found"));
        WorkshopLocation location = locationRepository.findByIdAndActiveTrue(locationId)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Location not found"));
        // 003.2 FR-039: an INACTIVE pair is refused with 409 (architecture Open decision 1 default).
        if (!availabilityService.isSellable(packageId, locationId)) {
            throw new WorkshopPackageNotAvailableException("Workshop Package is not sold at this location");
        }

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
                .map(session -> new AbstractMap.SimpleImmutableEntry<>(session, operationsFacade.getSaleEligibility(
                        new SaleEligibilityQuery(UUID.fromString(session.getReference())))))
                .filter(entry -> entry.getValue() != null && entry.getValue().customerSelectable())
                .map(entry -> toAvailability(entry.getKey(), entry.getValue(), location.getDisplayName()))
                .toList();
    }

    /**
     * Returns active locations that currently expose at least one selectable
     * customer session for the published package and requested date window.
     * This is intentionally narrower than the manager location list: public
     * booking must not show inactive or irrelevant operational locations.
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> discoverEligibleLocations(Long packageId, LocalDate from, LocalDate to) {
        packageRepository.findById(packageId)
                .filter(workshopPackage -> workshopPackage.getState() == WorkshopPackageState.PUBLISHED)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop Package not found"));

        // 003.2 FR-039: only locations where the Package is sellable (pair ACTIVE); an empty list is valid.
        java.util.Set<Long> sellable = new java.util.HashSet<>(availabilityService.activeLocationIds(packageId));
        return locationRepository.findByActiveTrue().stream()
                .filter(location -> sellable.contains(location.getId()))
                .sorted(java.util.Comparator.comparing(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation::getId))
                .map(location -> LocationResponse.builder()
                        .id(location.getId())
                        .code(location.getCode())
                        .name(location.getDisplayName())
                        .address(location.getAddressSnapshot())
                        .active(location.isActive())
                        .build())
                .toList();
    }

    private static CustomerSessionAvailabilityResponse toAvailability(
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession session,
            SaleEligibilitySnapshot eligibility, String locationName) {
        return new CustomerSessionAvailabilityResponse(
                UUID.fromString(session.getReference()),
                eligibility.locationReference(),
                locationName,
                eligibility.scheduledStart(),
                eligibility.scheduledEnd(),
                eligibility.saleState(),
                eligibility.customerSelectable(),
                eligibility.remainingCapacity());
    }
}
