package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scoped Workshop Session management service adapting to canonical WorkshopLocation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopSessionService {

    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopLocationService locationService;
    private final WorkshopSlotService slotService;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final AccountFacade accountFacade;
    private final OperationsAuditEventRepository auditEventRepository;
    private final CapacityHoldService capacityHoldService;

    @Transactional
    public SessionResponse createSession(String authHeader, SessionRequest request) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        validateSessionRequest(request);

        WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, request.getLocationId());
        WorkshopSlot slot = slotService.requireSlotEntityInScope(authHeader, request.getSlotId());

        if (!slot.getLocation().getId().equals(location.getId())) {
            throw new WorkshopValidationException("Configured slot does not belong to the target location");
        }

        ZonedDateTime now = Instant.now().atZone(WORKSHOP_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate workshopDate = request.getWorkshopDate();

        ZonedDateTime sessionStart = workshopDate.atTime(slot.getStartTime()).atZone(WORKSHOP_ZONE);
        if (!sessionStart.isAfter(now)) {
            throw new WorkshopValidationException("Workshop session start must be strictly in the future");
        }
        if (workshopDate.isAfter(today.plusDays(90))) {
            throw new WorkshopValidationException("Workshop session date cannot exceed 90 days in the future");
        }

        // Unique (location_id, workshop_date, slot_id)
        if (sessionRepository.existsByLocationIdAndWorkshopDateAndSlotId(location.getId(), workshopDate, slot.getId())) {
            log.warn("Duplicate session rejection: locationId={}, date={}, slotId={}", location.getId(), workshopDate, slot.getId());
            throw new WorkshopDuplicateSessionException("A session already exists for location " + location.getCode() +
                    ", date " + workshopDate + ", and slot " + slot.getLabel());
        }

        Instant scheduledStartAt = sessionStart.toInstant();
        Instant scheduledEndAt = workshopDate.atTime(slot.getEndTime()).atZone(WORKSHOP_ZONE).toInstant();

        int capacity = request.getCapacity() != null ? request.getCapacity() : slot.getDefaultCapacity();
        if (capacity < 1) {
            throw new WorkshopValidationException("Session capacity must be at least 1");
        }

        Instant checkInOpensAt;
        CheckInOpeningSource openingSource;
        if (request.getCheckInOpensAt() != null) {
            checkInOpensAt = request.getCheckInOpensAt();
            openingSource = CheckInOpeningSource.MANAGER_SET;
        } else {
            checkInOpensAt = scheduledStartAt.minusSeconds(3600); // Default 60 minutes before start
            openingSource = CheckInOpeningSource.DEFAULT;
        }

        SessionState state = SessionState.DRAFT;

        WorkshopSession session = WorkshopSession.builder()
                .reference(UUID.randomUUID().toString())
                .location(location)
                .slot(slot)
                .workshopDate(workshopDate)
                .scheduledStartAt(scheduledStartAt)
                .scheduledEndAt(scheduledEndAt)
                .checkInOpensAt(checkInOpensAt)
                .checkInOpeningSource(openingSource)
                .capacity(capacity)
                .state(state)
                .build();

        WorkshopSession saved = sessionRepository.save(session);
        log.info("Created workshop session id={}, reference={}, state={}, location={}",
                saved.getId(), saved.getReference(), saved.getState(), location.getCode());

        if (openingSource == CheckInOpeningSource.MANAGER_SET) {
            recordAudit(saved.getId(), actor.accountId(), "CHECK_IN_OVERRIDE_CONFIGURED", "SUCCESS",
                    "{\"checkInOpensAt\":\"" + checkInOpensAt + "\"}");
        }

        return toSessionResponse(saved);
    }

    @Transactional
    public BulkGenerationResponse generateBulkSessions(String authHeader, BulkSessionRequest request) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        validateBulkRequest(request);

        ZonedDateTime now = Instant.now().atZone(WORKSHOP_ZONE);
        LocalDate today = now.toLocalDate();
        if (request.getTo().isAfter(today.plusDays(90))) {
            throw new WorkshopValidationException("Bulk generation range cannot exceed 90 days in the future");
        }

        SessionState initialState = request.getInitialState() != null ? request.getInitialState() : SessionState.DRAFT;
        if (initialState != SessionState.DRAFT && initialState != SessionState.PUBLISHED) {
            throw new WorkshopValidationException("Initial state must be DRAFT or PUBLISHED");
        }

        WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, request.getLocationId());
        List<WorkshopSlot> slots = request.getSlotIds().stream()
                .map(slotId -> slotService.requireSlotEntityInScope(authHeader, slotId))
                .toList();

        for (WorkshopSlot slot : slots) {
            if (!slot.getLocation().getId().equals(location.getId())) {
                throw new WorkshopValidationException("Slot " + slot.getReference() + " does not belong to location " + location.getCode());
            }
        }

        List<String> createdSessionIds = new ArrayList<>();
        List<String> skippedDuplicates = new ArrayList<>();

        LocalDate current = request.getFrom();
        while (!current.isAfter(request.getTo())) {
            if (request.getWeekdays().contains(current.getDayOfWeek())) {
                for (WorkshopSlot slot : slots) {
                    ZonedDateTime sessionStart = current.atTime(slot.getStartTime()).atZone(WORKSHOP_ZONE);
                    if (!sessionStart.isAfter(now)) {
                        continue;
                    }

                    String duplicateKey = location.getCode() + "-" + current + "-" + slot.getLabel();
                    if (sessionRepository.existsByLocationIdAndWorkshopDateAndSlotId(location.getId(), current, slot.getId())) {
                        skippedDuplicates.add(duplicateKey);
                    } else {
                        Instant scheduledStartAt = sessionStart.toInstant();
                        Instant scheduledEndAt = current.atTime(slot.getEndTime()).atZone(WORKSHOP_ZONE).toInstant();
                        Instant checkInOpensAt = scheduledStartAt.minusSeconds(3600);

                        WorkshopSession session = WorkshopSession.builder()
                                .reference(UUID.randomUUID().toString())
                                .location(location)
                                .slot(slot)
                                .workshopDate(current)
                                .scheduledStartAt(scheduledStartAt)
                                .scheduledEndAt(scheduledEndAt)
                                .checkInOpensAt(checkInOpensAt)
                                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                                .capacity(slot.getDefaultCapacity())
                                .state(initialState)
                                .build();

                        WorkshopSession saved = sessionRepository.save(session);
                        createdSessionIds.add(saved.getReference());
                    }
                }
            }
            current = current.plusDays(1);
        }

        log.info("Bulk session generation completed: created={}, skippedDuplicates={}",
                createdSessionIds.size(), skippedDuplicates.size());

        return BulkGenerationResponse.builder()
                .createdSessionIds(createdSessionIds)
                .skippedDuplicates(skippedDuplicates)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> searchSessions(String authHeader, Long locationId,
                                                LocalDate from, LocalDate to, SessionState state) {
        AccountSummary actor = authorizationService.authenticate(authHeader);

        List<Long> allowedLocationIds;
        if (locationId != null) {
            WorkshopLocation location = locationService.requireLocationEntityInScope(authHeader, locationId);
            allowedLocationIds = List.of(location.getId());
        } else {
            if (authorizationService.isAllBranchActor(actor)) {
                allowedLocationIds = null;
            } else {
                allowedLocationIds = accountFacade.getLocationScope(actor.accountId());
                if (allowedLocationIds == null || allowedLocationIds.isEmpty()) {
                    return List.of();
                }
            }
        }

        List<WorkshopSession> sessions = sessionRepository.findAll();
        return sessions.stream()
                .filter(s -> allowedLocationIds == null || allowedLocationIds.contains(s.getLocation().getId()))
                .filter(s -> from == null || !s.getWorkshopDate().isBefore(from))
                .filter(s -> to == null || !s.getWorkshopDate().isAfter(to))
                .filter(s -> state == null || s.getState() == state)
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getSessionByReference(String authHeader, String sessionReference) {
        WorkshopSession session = requireSessionEntityInScope(authHeader, sessionReference);
        return toSessionResponse(session);
    }

    @Transactional
    public SessionResponse updateSession(String authHeader, String sessionReference, SessionRequest request) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        WorkshopSession session = requireSessionEntityInScope(authHeader, sessionReference);

        if (request == null) {
            throw new WorkshopValidationException("Session update request must not be null");
        }

        if (request.getCapacity() != null) {
            if (request.getCapacity() < 1) {
                throw new WorkshopValidationException("Capacity must be at least 1");
            }
            capacityHoldService.validateCapacityReduction(session.getId(), request.getCapacity());
            session.setCapacity(request.getCapacity());
        }

        if (request.getCheckInOpensAt() != null) {
            if (session.getState() == SessionState.IN_PROGRESS ||
                session.getState() == SessionState.ATTENDANCE_FINALIZED ||
                session.getState() == SessionState.CANCELLED) {
                throw new WorkshopValidationException("check_in_opens_at cannot be modified once session is IN_PROGRESS or finalized");
            }
            session.setCheckInOpensAt(request.getCheckInOpensAt());
            session.setCheckInOpeningSource(CheckInOpeningSource.MANAGER_SET);

            recordAudit(session.getId(), actor.accountId(), "CHECK_IN_OVERRIDE_UPDATED", "SUCCESS",
                    "{\"checkInOpensAt\":\"" + request.getCheckInOpensAt() + "\"}");
        }

        WorkshopSession updated = sessionRepository.save(session);
        return toSessionResponse(updated);
    }

    @Transactional(readOnly = true)
    public WorkshopSession requireSessionEntityInScope(String authHeader, String sessionReference) {
        WorkshopSession session = sessionRepository.findByReference(sessionReference)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found with reference " + sessionReference));

        locationService.requireLocationEntityInScope(authHeader, session.getLocation().getId());
        return session;
    }

    private void validateSessionRequest(SessionRequest request) {
        if (request == null) {
            throw new WorkshopValidationException("Session request must not be null");
        }
        if (request.getLocationId() == null) {
            throw new WorkshopValidationException("Location ID is required");
        }
        if (request.getSlotId() == null || request.getSlotId().isBlank()) {
            throw new WorkshopValidationException("Slot ID is required");
        }
        if (request.getWorkshopDate() == null) {
            throw new WorkshopValidationException("Workshop date is required");
        }
        if (request.getCapacity() != null && request.getCapacity() < 1) {
            throw new WorkshopValidationException("Capacity must be at least 1");
        }
    }

    private void validateBulkRequest(BulkSessionRequest request) {
        if (request == null) {
            throw new WorkshopValidationException("Bulk session request must not be null");
        }
        if (request.getLocationId() == null) {
            throw new WorkshopValidationException("Location ID is required");
        }
        if (request.getSlotIds() == null || request.getSlotIds().isEmpty()) {
            throw new WorkshopValidationException("At least one slot ID is required");
        }
        if (request.getFrom() == null || request.getTo() == null) {
            throw new WorkshopValidationException("From and To dates are required");
        }
        if (request.getFrom().isAfter(request.getTo())) {
            throw new WorkshopValidationException("From date cannot be after To date");
        }
        if (request.getWeekdays() == null || request.getWeekdays().isEmpty()) {
            throw new WorkshopValidationException("At least one weekday must be specified");
        }
    }

    private void recordAudit(Long sessionId, Long actorId, String action, String outcome, String metadata) {
        try {
            OperationsAuditEvent event = OperationsAuditEvent.builder()
                    .sessionId(sessionId)
                    .actorId(actorId)
                    .action(action)
                    .outcome(outcome)
                    .correlationId(UUID.randomUUID().toString())
                    .metadata(metadata)
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record audit event: action={}, error={}", action, e.getMessage());
        }
    }

    private SessionResponse toSessionResponse(WorkshopSession session) {
        return SessionResponse.builder()
                .id(session.getReference())
                .locationId(session.getLocation().getId())
                .slotId(session.getSlot().getReference())
                .workshopDate(session.getWorkshopDate())
                .scheduledStartAt(session.getScheduledStartAt())
                .scheduledEndAt(session.getScheduledEndAt())
                .checkInOpensAt(session.getCheckInOpensAt())
                .checkInOpeningSource(session.getCheckInOpeningSource())
                .capacity(session.getCapacity())
                .state(session.getState())
                .operationalLeadAccountId(null)
                .build();
    }
}
