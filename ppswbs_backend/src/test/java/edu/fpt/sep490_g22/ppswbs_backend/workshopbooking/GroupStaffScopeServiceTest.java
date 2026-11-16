package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLineSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupStaffScopeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 003.1 T135 (FR-048, FR-017b7, CHK025): Staff need scope AND an assignment on the same line; a Manager is global. */
class GroupStaffScopeServiceTest {

    private static final long LOCATION_A = 10L;
    private static final long LOCATION_B = 20L;
    private WorkshopBookingAuthorizationService authorization;
    private WorkshopOperationsAdapter operations;
    private AccountFacade accounts;
    private GroupStaffScopeService scope;
    private BookingGroup group;
    private BookingGroupLine lineA;
    private BookingGroupLine lineB;

    @BeforeEach
    void setUp() {
        authorization = mock(WorkshopBookingAuthorizationService.class);
        operations = mock(WorkshopOperationsAdapter.class);
        accounts = mock(AccountFacade.class);
        BookingGroupLineRepository lines = mock(BookingGroupLineRepository.class);
        scope = new GroupStaffScopeService(authorization, lines, operations, accounts);
        group = BookingGroup.builder().id(1L).groupCode("GRP-SCOPE0001").build();
        lineA = line("L-A", LOCATION_A);
        lineB = line("L-B", LOCATION_B);
        when(lines.findByGroupIdOrderByLineIndexAsc(1L)).thenReturn(List.of(lineA, lineB));
    }

    private static BookingGroupLine line(String reference, long location) {
        BookingGroupLine line = BookingGroupLine.builder().lineReference(reference).build();
        BookingGroupLineSession session = new BookingGroupLineSession();
        session.setLocationReference(location);
        line.setSessions(List.of(session));
        return line;
    }

    private AccountSummary staff(long id, List<Long> locations, boolean assignedA, boolean assignedB) {
        AccountSummary account = new AccountSummary(id, ActorRole.STAFF, ActorStatus.ACTIVE, "s@x.test", Instant.now(), Instant.now());
        when(authorization.isStaff(account)).thenReturn(true);
        when(accounts.getLocationScope(id)).thenReturn(locations);
        when(operations.isStaffAssignedToLine(id, "L-A")).thenReturn(assignedA);
        when(operations.isStaffAssignedToLine(id, "L-B")).thenReturn(assignedB);
        return account;
    }

    @Test
    void scopeAndAssignmentAtTheSameLocationAllowTheWholeGroupEvenWithALineElsewhere() {
        AccountSummary a = staff(1L, List.of(LOCATION_A), true, false);

        assertThat(scope.canOperate(a, group)).isTrue();
        assertThat(scope.canOperateOnLine(a, lineA)).isTrue();
        assertThat(scope.canOperateOnLine(a, lineB)).isFalse(); // a single ticket follows its own line
    }

    @Test
    void anAssignmentOnlyAtALocationOutsideTheScopeIsRefused() {
        AccountSummary b = staff(2L, List.of(LOCATION_A), false, true);

        assertThat(scope.canOperate(b, group)).isFalse();
    }

    @Test
    void scopeForALocationWithoutAnAssignedSessionOfTheGroupNeverAuthorizes() {
        AccountSummary onlyScope = staff(3L, List.of(LOCATION_A, LOCATION_B), false, false);

        assertThat(scope.canOperate(onlyScope, group)).isFalse();
    }

    @Test
    void anInactiveOrScopelessStaffMemberIsRefused() {
        AccountSummary noScope = staff(4L, List.of(), true, true);
        AccountSummary inactive = new AccountSummary(5L, ActorRole.STAFF, ActorStatus.SUSPENDED, "i@x.test", Instant.now(), Instant.now());
        when(authorization.isStaff(inactive)).thenReturn(true);

        assertThat(scope.canOperate(noScope, group)).isFalse();
        assertThat(scope.canOperate(inactive, group)).isFalse();
        assertThat(scope.canOperate(null, group)).isFalse();
    }

    @Test
    void anActiveManagerIsGlobalWithoutScopeOrAssignment() {
        AccountSummary manager = new AccountSummary(6L, ActorRole.MANAGER, ActorStatus.ACTIVE, "m@x.test", Instant.now(), Instant.now());
        when(authorization.isManager(manager)).thenReturn(true);

        assertThat(scope.canOperate(manager, group)).isTrue();
        assertThat(scope.canOperateOnLine(manager, lineB)).isTrue();
        verifyNoInteractions(operations);
    }

    @Test
    void anAdminOrMemberIsNeverAllowed() {
        AccountSummary admin = new AccountSummary(7L, ActorRole.ADMIN, ActorStatus.ACTIVE, "a@x.test", Instant.now(), Instant.now());

        assertThat(scope.canOperate(admin, group)).isFalse();
    }
}
