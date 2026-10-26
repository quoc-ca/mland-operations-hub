package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBranchRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBranch;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T015: Unit and boundary verification tests for {@link WorkshopBranchService}.
 * <p>
 * Verifies:
 * 1. Only all-branch / higher-privilege actors (e.g. ADMIN) can create branches.
 * 2. Managers cannot create unassigned branches; Operations does not create or infer Manager-to-branch assignments.
 * 3. Managers only list and read branches within their assigned location scope; no cross-Manager branch data leakage.
 * 4. Preservation of branch history (duplicate branch codes rejected; no hard-delete).
 * 5. Complete decoupling from internal Account Management entity enums.
 */
@ExtendWith(MockitoExtension.class)
public class WorkshopBranchServiceTest {

    @Mock
    private WorkshopBranchRepository branchRepository;

    @Mock
    private WorkshopOperationsAuthorizationService authorizationService;

    @Mock
    private AccountFacade accountFacade;

    @InjectMocks
    private WorkshopBranchService branchService;

    private static final String AUTH_HEADER = "Bearer test-token";
    private static final Long MANAGER_A_ID = 10L;
    private static final Long MANAGER_B_ID = 20L;
    private static final Long STAFF_ID = 30L;
    private static final Long MEMBER_ID = 40L;
    private static final Long ADMIN_ID = 99L;

    private WorkshopBranch branch1;
    private WorkshopBranch branch2;

    private AccountSummary adminAccount;
    private AccountSummary managerAccountA;
    private AccountSummary staffAccount;
    private AccountSummary memberAccount;

    @BeforeEach
    void setUp() {
        branch1 = WorkshopBranch.builder()
                .id(1L)
                .reference(UUID.randomUUID().toString())
                .code("BR-01")
                .displayName("Branch One")
                .addressSnapshot("123 Street 1")
                .active(true)
                .build();

        branch2 = WorkshopBranch.builder()
                .id(2L)
                .reference(UUID.randomUUID().toString())
                .code("BR-02")
                .displayName("Branch Two")
                .addressSnapshot("456 Street 2")
                .active(true)
                .build();

        adminAccount = mock(AccountSummary.class);
        lenient().when(adminAccount.accountId()).thenReturn(ADMIN_ID);

        managerAccountA = mock(AccountSummary.class);
        lenient().when(managerAccountA.accountId()).thenReturn(MANAGER_A_ID);

        staffAccount = mock(AccountSummary.class);
        lenient().when(staffAccount.accountId()).thenReturn(STAFF_ID);

        memberAccount = mock(AccountSummary.class);
        lenient().when(memberAccount.accountId()).thenReturn(MEMBER_ID);
    }

    // =========================================================================
    // 1. Branch Listing Tests (Scoped Visibility & Non-Disclosure)
    // =========================================================================

    @Nested
    @DisplayName("Branch Listing Tests")
    class BranchListingTests {

        @Test
        @DisplayName("All-branch actor (Admin) lists all active branches without location restriction")
        void adminListsAllActiveBranches() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(adminAccount);
            when(authorizationService.isAllBranchActor(adminAccount)).thenReturn(true);
            when(branchRepository.findByActiveTrue()).thenReturn(List.of(branch1, branch2));

            List<BranchResponse> responses = branchService.listBranches(AUTH_HEADER);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getCode()).isEqualTo("BR-01");
            assertThat(responses.get(1).getCode()).isEqualTo("BR-02");
            verify(accountFacade, never()).getLocationScope(any());
        }

        @Test
        @DisplayName("Manager A only sees branches in their location scope; does NOT expose Manager B branches")
        void managerOnlySeesInScopeBranches() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of(1L));
            when(branchRepository.findByIdInAndActiveTrue(List.of(1L))).thenReturn(List.of(branch1));

            List<BranchResponse> responses = branchService.listBranches(AUTH_HEADER);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(branch1.getReference());
            assertThat(responses.get(0).getCode()).isEqualTo("BR-01");

            // Verify branch2 was not exposed
            assertThat(responses.stream().noneMatch(r -> "BR-02".equals(r.getCode()))).isTrue();
        }

        @Test
        @DisplayName("Unscoped Manager receives empty list without disclosing any branch")
        void unscopedManagerReceivesEmptyList() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of());

            List<BranchResponse> responses = branchService.listBranches(AUTH_HEADER);

            assertThat(responses).isEmpty();
            verify(branchRepository, never()).findByIdInAndActiveTrue(any());
            verify(branchRepository, never()).findByActiveTrue();
        }

        @Test
        @DisplayName("Staff with location scope lists only their scoped branches")
        void staffWithScopeListsBranches() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(staffAccount);
            when(authorizationService.isAllBranchActor(staffAccount)).thenReturn(false);
            when(authorizationService.isManager(staffAccount)).thenReturn(false);
            when(authorizationService.isStaff(staffAccount)).thenReturn(true);
            when(accountFacade.getLocationScope(STAFF_ID)).thenReturn(List.of(2L));
            when(branchRepository.findByIdInAndActiveTrue(List.of(2L))).thenReturn(List.of(branch2));

            List<BranchResponse> responses = branchService.listBranches(AUTH_HEADER);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getCode()).isEqualTo("BR-02");
        }

        @Test
        @DisplayName("Unauthorized role (e.g. Member) is denied from listing branches with WorkshopForbiddenException")
        void memberIsDenied() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(memberAccount);
            when(authorizationService.isAllBranchActor(memberAccount)).thenReturn(false);
            when(authorizationService.isManager(memberAccount)).thenReturn(false);
            when(authorizationService.isStaff(memberAccount)).thenReturn(false);

            assertThatThrownBy(() -> branchService.listBranches(AUTH_HEADER))
                    .isInstanceOf(WorkshopForbiddenException.class)
                    .hasMessageContaining("cannot access workshop branches");
        }
    }

    // =========================================================================
    // 2. Branch Creation Tests (Only All-Branch Actor Can Create)
    // =========================================================================

    @Nested
    @DisplayName("Branch Creation Tests")
    class BranchCreationTests {

        @Test
        @DisplayName("All-branch actor (Admin) can create a new branch successfully")
        void allBranchActorCreatesBranchSuccessfully() {
            when(authorizationService.requireAllBranchActor(AUTH_HEADER)).thenReturn(adminAccount);
            when(branchRepository.findByCode("BR-NEW")).thenReturn(Optional.empty());
            when(branchRepository.save(any(WorkshopBranch.class))).thenAnswer(invocation -> {
                WorkshopBranch b = invocation.getArgument(0);
                b.setId(3L);
                return b;
            });

            BranchRequest request = BranchRequest.builder()
                    .code("BR-NEW")
                    .name("New Workshop Branch")
                    .address("789 Central Ave")
                    .active(true)
                    .build();

            BranchResponse response = branchService.createBranch(AUTH_HEADER, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isNotNull();
            assertThat(response.getCode()).isEqualTo("BR-NEW");
            assertThat(response.getName()).isEqualTo("New Workshop Branch");
            assertThat(response.getActive()).isTrue();
            verify(branchRepository).save(any(WorkshopBranch.class));
            // Verify Operations does not create any manager assignment
            verify(accountFacade, never()).getLocationScope(any());
        }

        @Test
        @DisplayName("Manager cannot create branch (even if scoped); denied with WorkshopForbiddenException")
        void managerCannotCreateBranch() {
            when(authorizationService.requireAllBranchActor(AUTH_HEADER))
                    .thenThrow(new WorkshopForbiddenException("Access requires all-branch higher-privilege authority"));

            BranchRequest request = BranchRequest.builder()
                    .code("BR-MGR")
                    .name("Manager Branch Attempt")
                    .build();

            assertThatThrownBy(() -> branchService.createBranch(AUTH_HEADER, request))
                    .isInstanceOf(WorkshopForbiddenException.class)
                    .hasMessageContaining("all-branch higher-privilege authority");

            verify(branchRepository, never()).save(any());
        }

        @Test
        @DisplayName("Staff cannot create branch; denied with WorkshopForbiddenException")
        void staffDeniedBranchCreation() {
            when(authorizationService.requireAllBranchActor(AUTH_HEADER))
                    .thenThrow(new WorkshopForbiddenException("Access requires all-branch higher-privilege authority"));

            BranchRequest request = BranchRequest.builder()
                    .code("BR-STAFF")
                    .name("Staff Branch")
                    .build();

            assertThatThrownBy(() -> branchService.createBranch(AUTH_HEADER, request))
                    .isInstanceOf(WorkshopForbiddenException.class)
                    .hasMessageContaining("all-branch higher-privilege authority");

            verify(branchRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate branch code is rejected with WorkshopValidationException (preserves history)")
        void duplicateBranchCodeRejected() {
            when(authorizationService.requireAllBranchActor(AUTH_HEADER)).thenReturn(adminAccount);
            when(branchRepository.findByCode("BR-01")).thenReturn(Optional.of(branch1));

            BranchRequest request = BranchRequest.builder()
                    .code("BR-01")
                    .name("Duplicate Branch")
                    .build();

            assertThatThrownBy(() -> branchService.createBranch(AUTH_HEADER, request))
                    .isInstanceOf(WorkshopValidationException.class)
                    .hasMessageContaining("already exists");

            verify(branchRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blank code or name is rejected with WorkshopValidationException")
        void blankCodeOrNameRejected() {
            when(authorizationService.requireAllBranchActor(AUTH_HEADER)).thenReturn(adminAccount);

            BranchRequest blankCode = BranchRequest.builder().code("  ").name("Valid Name").build();
            assertThatThrownBy(() -> branchService.createBranch(AUTH_HEADER, blankCode))
                    .isInstanceOf(WorkshopValidationException.class)
                    .hasMessageContaining("Branch code is required");

            BranchRequest blankName = BranchRequest.builder().code("BR-VALID").name(" ").build();
            assertThatThrownBy(() -> branchService.createBranch(AUTH_HEADER, blankName))
                    .isInstanceOf(WorkshopValidationException.class)
                    .hasMessageContaining("Branch name is required");

            BranchRequest nullRequest = null;
            assertThatThrownBy(() -> branchService.createBranch(AUTH_HEADER, nullRequest))
                    .isInstanceOf(WorkshopValidationException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    // =========================================================================
    // 3. Branch Retrieval & Scope Verification Tests
    // =========================================================================

    @Nested
    @DisplayName("Branch Retrieval & Scope Verification Tests")
    class BranchRetrievalTests {

        @Test
        @DisplayName("Manager retrieves their own in-scope branch successfully")
        void managerRetrievesInScopeBranch() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(branchRepository.findByReference(branch1.getReference())).thenReturn(Optional.of(branch1));
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of(1L));

            BranchResponse response = branchService.getBranchByReference(AUTH_HEADER, branch1.getReference());

            assertThat(response).isNotNull();
            assertThat(response.getCode()).isEqualTo("BR-01");
        }

        @Test
        @DisplayName("Manager attempting to view another Manager's branch is denied with WorkshopBranchScopeException")
        void managerDeniedViewingOtherManagerBranch() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(branchRepository.findByReference(branch2.getReference())).thenReturn(Optional.of(branch2));
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of(1L)); // Only has branch 1

            assertThatThrownBy(() -> branchService.getBranchByReference(AUTH_HEADER, branch2.getReference()))
                    .isInstanceOf(WorkshopBranchScopeException.class)
                    .hasMessageContaining("outside actor's active location scope");
        }

        @Test
        @DisplayName("All-branch actor (Admin) can retrieve any branch regardless of location scope")
        void adminRetrievesAnyBranch() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(adminAccount);
            when(authorizationService.isAllBranchActor(adminAccount)).thenReturn(true);
            when(branchRepository.findByReference(branch2.getReference())).thenReturn(Optional.of(branch2));

            BranchResponse response = branchService.getBranchByReference(AUTH_HEADER, branch2.getReference());

            assertThat(response).isNotNull();
            assertThat(response.getCode()).isEqualTo("BR-02");
            verify(accountFacade, never()).getLocationScope(any());
        }

        @Test
        @DisplayName("Retrieving non-existent branch throws WorkshopNotFoundException")
        void notFoundBranchThrowsException() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(branchRepository.findByReference("unknown-ref")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> branchService.getBranchByReference(AUTH_HEADER, "unknown-ref"))
                    .isInstanceOf(WorkshopNotFoundException.class);
        }

        @Test
        @DisplayName("requireBranchEntityInScope succeeds for in-scope entity and fails for out-of-scope")
        void requireBranchEntityInScopeChecksScope() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(branchRepository.findById(1L)).thenReturn(Optional.of(branch1));
            when(branchRepository.findById(2L)).thenReturn(Optional.of(branch2));
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of(1L));

            // In scope
            WorkshopBranch entity = branchService.requireBranchEntityInScope(AUTH_HEADER, 1L);
            assertThat(entity).isEqualTo(branch1);

            // Out of scope
            assertThatThrownBy(() -> branchService.requireBranchEntityInScope(AUTH_HEADER, 2L))
                    .isInstanceOf(WorkshopBranchScopeException.class);
        }
    }
}
