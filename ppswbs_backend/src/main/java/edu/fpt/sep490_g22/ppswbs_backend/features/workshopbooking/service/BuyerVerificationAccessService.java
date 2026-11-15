package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may request and confirm a buyer-verification OTP with a bearer instead of a contact value (003.1 FR-048, T148):
 * any active Manager (global) and an active Staff member within the Staff scope of the group (scope AND assignment on a
 * line of the group). A Member, an Admin, a Staff member without authority and a wrong or unknown group are refused:
 * 403 only for a known Staff member lacking authority, otherwise 404; a missing or invalid credential is 401.
 */
@Service
@RequiredArgsConstructor
public class BuyerVerificationAccessService {

    private final WorkshopBookingAuthorizationService authorization;
    private final GroupStaffScopeService staffScope;
    private final BookingGroupRepository groups;

    @Transactional(readOnly = true)
    public AccountSummary requireInternalAuthority(String authorizationHeader, String groupCode, String correlation) {
        AccountSummary account = authorization.authenticate(authorizationHeader);
        BookingGroup group = groupCode == null ? null : groups.findByGroupCode(groupCode.trim()).orElse(null);
        if (group == null) {
            throw notFound();
        }
        boolean manager = authorization.isManager(account);
        boolean staff = authorization.isStaff(account);
        if (!manager && !staff) {
            authorization.recordDeniedAttempt(group.getGroupCode(), 0L, account.role().name(), account.accountId(),
                    "BUYER_VERIFICATION", correlation, "Role not allowed");
            throw notFound();
        }
        if (!staffScope.canOperate(account, group)) {
            authorization.recordDeniedAttempt(group.getGroupCode(), 0L, account.role().name(), account.accountId(),
                    "BUYER_VERIFICATION", correlation, "Outside the Staff scope or assignment");
            throw new WorkshopBookingProblemException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Caller is not authorized for this booking group");
        }
        return account;
    }

    private static WorkshopBookingProblemException notFound() {
        return new WorkshopBookingProblemException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
    }
}
