package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLineSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Staff scope of group-level operations (003.1 FR-048, FR-017b7, decided 2026-11-15, CHK025): an active Manager is
 * global; an active Staff member may act only through a line of the group that they are actively assigned to (the
 * 003.2 session-assignment rule) AND whose location is in their location scope. The assignment is the only link: scope
 * for a location where the Staff member has no assigned session of the group never authorizes anything, and scope
 * for one location does not extend to lines at another. A single-ticket operation follows that ticket's own line.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupStaffScopeService {

    private final WorkshopBookingAuthorizationService authorization;
    private final BookingGroupLineRepository lines;
    private final WorkshopOperationsAdapter operations;
    private final AccountFacade accountFacade;

    /** Group-level operation (resend confirmation, buyer verification): any line of the group may satisfy the rule. */
    public boolean canOperate(AccountSummary account, BookingGroup group) {
        if (isActiveManager(account)) {
            return true;
        }
        if (!isActiveStaff(account)) {
            return false;
        }
        return lines.findByGroupIdOrderByLineIndexAsc(group.getId()).stream().anyMatch(line -> staffCovers(account, line));
    }

    /** Single-ticket operation (recipient change): only the ticket's own line counts. */
    public boolean canOperateOnLine(AccountSummary account, BookingGroupLine line) {
        if (isActiveManager(account)) {
            return true;
        }
        return isActiveStaff(account) && staffCovers(account, line);
    }

    private boolean staffCovers(AccountSummary account, BookingGroupLine line) {
        Set<Long> locations = currentLocations(line);
        if (locations.isEmpty()) {
            return false;
        }
        List<Long> scope = accountFacade.getLocationScope(account.accountId());
        return scope != null && locations.stream().anyMatch(scope::contains)
                && operations.isStaffAssignedToLine(account.accountId(), line.getLineReference());
    }

    /**
     * Current locations of the line (003.1 FR-053, FR-054, Q4): the locations of the sessions its ACTIVE tickets hold
     * in Operations now, so a ticket moved to another location is judged there. Before any ACTIVE Operations ticket
     * exists the booking-time copy is the answer (Q1). Operations unavailable fails closed with 503 (Q3): a stale
     * location must never grant or deny access.
     */
    private Set<Long> currentLocations(BookingGroupLine line) {
        DeliverySafeTicketSet projection = line.getLineReference() == null ? null
                : DependencyGuard.call("Workshop Operations",
                        () -> operations.getBookingTicketProjection(line.getLineReference()));
        Set<Long> current = new LinkedHashSet<>();
        if (projection != null) {
            projection.tickets().forEach(ticket -> ticket.sessionEntitlements().stream()
                    .map(SessionEntitlementProjection::locationReference).filter(Objects::nonNull).forEach(current::add));
        }
        if (!current.isEmpty()) {
            return current;
        }
        line.getSessions().stream().map(BookingGroupLineSession::getLocationReference).filter(Objects::nonNull)
                .forEach(current::add);
        return current;
    }

    /**
     * Location written on the audit row of a group-level command (FR-054, Q4; no access effect): the current location
     * of the ticket (or, with no ticket reference, the line) from Operations. An audit row must not block the command, so
     * when Operations cannot answer the booking-time copy is written and a WARN is logged.
     */
    public Long auditLocationOf(BookingGroupLine line, String operationsTicketReference) {
        Long copy = line.getSessions().stream().map(BookingGroupLineSession::getLocationReference)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (line.getLineReference() == null) {
            return copy;
        }
        try {
            DeliverySafeTicketSet projection = operations.getBookingTicketProjection(line.getLineReference());
            if (projection == null) {
                return copy;
            }
            return projection.tickets().stream()
                    .filter(t -> operationsTicketReference == null || (t.ticketReference() != null
                            && operationsTicketReference.equals(t.ticketReference().toString())))
                    .flatMap(t -> t.sessionEntitlements().stream())
                    .sorted(java.util.Comparator.comparing(SessionEntitlementProjection::scheduledStart,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .map(SessionEntitlementProjection::locationReference).filter(Objects::nonNull)
                    .findFirst().orElse(copy);
        } catch (RuntimeException ex) {
            log.warn("Current location of line {} could not be read; the booking-time copy is audited: {}",
                    line.getLineReference(), ex.getClass().getSimpleName());
            return copy;
        }
    }

    private boolean isActiveManager(AccountSummary account) {
        return account != null && account.status() == ActorStatus.ACTIVE && authorization.isManager(account);
    }

    private boolean isActiveStaff(AccountSummary account) {
        return account != null && account.status() == ActorStatus.ACTIVE && authorization.isStaff(account);
    }
}
