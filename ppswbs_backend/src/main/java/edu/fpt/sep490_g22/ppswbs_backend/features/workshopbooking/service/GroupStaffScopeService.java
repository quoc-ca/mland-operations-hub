package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLineSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Staff scope of group-level operations (003.1 FR-048, FR-017b7, decided 2026-11-15, CHK025): an active Manager is
 * global; an active Staff member may act only through a line of the group that they are actively assigned to (the
 * 003.2 session-assignment rule) AND whose location is in their location scope. The assignment is the only link: scope
 * for a location where the Staff member has no assigned session of the group never authorizes anything, and scope
 * for one location does not extend to lines at another. A single-ticket operation follows that ticket's own line.
 */
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
        Long location = line.getSessions().stream().map(BookingGroupLineSession::getLocationReference)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (location == null) {
            return false;
        }
        List<Long> scope = accountFacade.getLocationScope(account.accountId());
        return scope != null && scope.contains(location)
                && operations.isStaffAssignedToLine(account.accountId(), line.getLineReference());
    }

    private boolean isActiveManager(AccountSummary account) {
        return account != null && account.status() == ActorStatus.ACTIVE && authorization.isManager(account);
    }

    private boolean isActiveStaff(AccountSummary account) {
        return account != null && account.status() == ActorStatus.ACTIVE && authorization.isStaff(account);
    }
}
