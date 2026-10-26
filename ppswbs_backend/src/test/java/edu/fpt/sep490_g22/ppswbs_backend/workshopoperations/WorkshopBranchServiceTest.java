package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.BranchResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopBranchService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopValidationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkshopBranchServiceTest {

    @Mock
    private WorkshopLocationRepository locationRepository;

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

    private WorkshopLocation branch1;
    private WorkshopLocation branch2;

    private AccountSummary adminAccount;
    private AccountSummary managerAccountA;
    private AccountSummary staffAccount;
    private AccountSummary memberAccount;

    @BeforeEach
    void setUp() {
        branch1 = WorkshopLocation.builder()
                .id(1L)
                .code("BR-01")
                .displayName("Branch One")
                .addressSnapshot("123 Street 1")
                .active(true)
                .build();

        branch2 = WorkshopLocation.builder()
                .id(2L)
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

    @Nested
    @DisplayName("Branch Listing Tests")
    class ListBranchesTests {

        @Test
        @DisplayName("All-branch actor (Admin) lists all active branches")
        void adminListsAllBranches() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(adminAccount);
            when(authorizationService.isAllBranchActor(adminAccount)).thenReturn(true);
            when(locationRepository.findByActiveTrue()).thenReturn(List.of(branch1, branch2));

            List<BranchResponse> responses = branchService.listBranches(AUTH_HEADER);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getCode()).isEqualTo("BR-01");
            assertThat(responses.get(1).getCode()).isEqualTo("BR-02");
            verify(locationRepository).findByActiveTrue();
            verify(accountFacade, never()).getLocationScope(any());
        }

        @Test
        @DisplayName("Manager lists only branches within their location scope")
        void managerListsScopedBranchesOnly() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of(1L));
            when(locationRepository.findByIdInAndActiveTrue(List.of(1L))).thenReturn(List.of(branch1));

            List<BranchResponse> responses = branchService.listBranches(AUTH_HEADER);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getCode()).isEqualTo("BR-01");
            verify(locationRepository).findByIdInAndActiveTrue(List.of(1L));
            verify(locationRepository, never()).findByActiveTrue();
        }

        @Test
        @DisplayName("Manager with empty location scope receives empty list without error")
        void managerWithEmptyScopeReceivesEmptyList() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of());

            List<BranchResponse> responses = branchService.listBranches(AUTH_HEADER);

            assertThat(responses).isEmpty();
            verify(locationRepository, never()).findByIdInAndActiveTrue(any());
        }

        @Test
        @DisplayName("Member role is denied with WorkshopForbiddenException when listing branches")
        void memberDeniedFromListingBranches() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(memberAccount);
            when(authorizationService.isAllBranchActor(memberAccount)).thenReturn(false);
            when(authorizationService.isManager(memberAccount)).thenReturn(false);
            when(authorizationService.isStaff(memberAccount)).thenReturn(false);

            assertThatThrownBy(() -> branchService.listBranches(AUTH_HEADER))
                    .isInstanceOf(WorkshopForbiddenException.class)
                    .hasMessageContaining("Actor cannot access workshop locations");
        }
    }

    @Nested
    @DisplayName("Branch Creation Tests")
    class CreateBranchTests {

        @Test
        @DisplayName("All-branch actor (Admin) successfully creates a new branch")
        void adminCreatesBranchSuccessfully() {
            BranchRequest request = BranchRequest.builder()
                    .code("BR-03")
                    .name("District 3 Workshop")
                    .address("789 Vo Van Tan, D3, HCMC")
                    .active(true)
                    .build();

            when(authorizationService.requireAllBranchActor(AUTH_HEADER)).thenReturn(adminAccount);
            when(locationRepository.findByCode("BR-03")).thenReturn(Optional.empty());
            when(locationRepository.save(any(WorkshopLocation.class))).thenAnswer(invocation -> {
                WorkshopLocation b = invocation.getArgument(0);
                b.setId(3L);
                return b;
            });

            BranchResponse response = branchService.createBranch(AUTH_HEADER, request);

            assertThat(response).isNotNull();
            assertThat(response.getCode()).isEqualTo("BR-03");
            assertThat(response.getName()).isEqualTo("District 3 Workshop");
            verify(locationRepository).save(any(WorkshopLocation.class));
        }

        @Test
        @DisplayName("Duplicate branch code is rejected with WorkshopValidationException")
        void duplicateBranchCodeRejected() {
            BranchRequest request = BranchRequest.builder()
                    .code("BR-01")
                    .name("Duplicate Branch")
                    .address("Some Address")
                    .build();

            when(authorizationService.requireAllBranchActor(AUTH_HEADER)).thenReturn(adminAccount);
            when(locationRepository.findByCode("BR-01")).thenReturn(Optional.of(branch1));

            assertThatThrownBy(() -> branchService.createBranch(AUTH_HEADER, request))
                    .isInstanceOf(WorkshopValidationException.class)
                    .hasMessageContaining("already exists");

            verify(locationRepository, never()).save(any());
        }
    }
}
