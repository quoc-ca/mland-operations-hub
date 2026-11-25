package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.DaySummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * navigation shell and dashboard (014 R-1, T023): read-only counts for one Vietnam day. Manager only. "Sessions" counts every session of
 * the day in any state (the same set the session list returns for that day), "cancelled" those in state CANCELLED;
 * "expected" and "checked in" count people holding a valid ticket for the sessions that are not cancelled (the same
 * people the attendance roster lists), never the sessions' capacity. Writes nothing and writes no audit row.
 */
@Service
@RequiredArgsConstructor
public class WorkshopDaySummaryService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkshopSessionRepository sessions;
    private final TicketSessionEntitlementRepository entitlements;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final WorkshopLocationService locationService;

    @Transactional(readOnly = true)
    public DaySummaryResponse summarize(String authHeader, LocalDate date, Long locationId) {
        AccountSummary actor = authorizationService.authenticate(authHeader);
        if (!authorizationService.isManager(actor)) {
            throw new WorkshopForbiddenException("Access denied: only a Manager reads the day summary");
        }
        if (locationId != null) {
            locationService.requireLocationEntityInScope(authHeader, locationId);
        }
        LocalDate day = date != null ? date : LocalDate.now(VIETNAM);
        return new DaySummaryResponse(day,
                sessions.countOfDay(day, locationId),
                sessions.countOfDayInState(day, SessionState.CANCELLED, locationId),
                entitlements.countRosterOfDay(day, locationId, SessionState.CANCELLED, TicketValidityState.ACTIVE,
                        AttendanceState.CHECKED_IN),
                entitlements.countRosterOfDay(day, locationId, SessionState.CANCELLED, TicketValidityState.ACTIVE, null));
    }
}
