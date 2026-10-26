package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLocationService;
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
public class WorkshopLocationServiceTest {

    @Mock
    private WorkshopLocationRepository locationRepository;

    @Mock
    private WorkshopOperationsAuthorizationService authorizationService;

    @Mock
    private AccountFacade accountFacade;

    @InjectMocks
    private WorkshopLocationService locationService;

    private static final String AUTH_HEADER = "Bearer test-token";
    private static final Long MANAGER_A_ID = 10L;
    private static final Long MANAGER_B_ID = 20L;
    private static final Long STAFF_ID = 30L;
    private static final Long MEMBER_ID = 40L;
    private static final Long ADMIN_ID = 99L;

    private WorkshopLocation location1;
    private WorkshopLocation location2;

    private AccountSummary adminAccount;
    private AccountSummary managerAccountA;
    private AccountSummary staffAccount;
    private AccountSummary memberAccount;

    @BeforeEach
    void setUp() {
        location1 = WorkshopLocation.builder()
                .id(1L)
                .code("LOC-01")
                .displayName("Location One")
                .addressSnapshot("123 Street 1")
                .active(true)
                .build();

        location2 = WorkshopLocation.builder()
                .id(2L)
                .code("LOC-02")
                .displayName("Location Two")
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
    @DisplayName("Location Listing Tests")
    class ListLocationsTests {

        @Test
        @DisplayName("All-location actor (Admin) lists all active locations")
        void adminListsAllLocations() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(adminAccount);
            when(authorizationService.isAllBranchActor(adminAccount)).thenReturn(true);
            when(locationRepository.findByActiveTrue()).thenReturn(List.of(location1, location2));

            List<LocationResponse> responses = locationService.listLocations(AUTH_HEADER);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getCode()).isEqualTo("LOC-01");
            assertThat(responses.get(0).getId()).isEqualTo(1L);
            assertThat(responses.get(1).getCode()).isEqualTo("LOC-02");
            assertThat(responses.get(1).getId()).isEqualTo(2L);
            verify(locationRepository).findByActiveTrue();
            verify(accountFacade, never()).getLocationScope(any());
        }

        @Test
        @DisplayName("Manager lists only locations within their location scope")
        void managerListsScopedLocationsOnly() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isAllBranchActor(managerAccountA)).thenReturn(false);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(accountFacade.getLocationScope(MANAGER_A_ID)).thenReturn(List.of(1L));
            when(locationRepository.findByIdInAndActiveTrue(List.of(1L))).thenReturn(List.of(location1));

            List<LocationResponse> responses = locationService.listLocations(AUTH_HEADER);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getCode()).isEqualTo("LOC-01");
            assertThat(responses.get(0).getId()).isEqualTo(1L);
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

            List<LocationResponse> responses = locationService.listLocations(AUTH_HEADER);

            assertThat(responses).isEmpty();
            verify(locationRepository, never()).findByIdInAndActiveTrue(any());
        }

        @Test
        @DisplayName("Member role is denied with WorkshopForbiddenException when listing locations")
        void memberDeniedFromListingLocations() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(memberAccount);
            when(authorizationService.isAllBranchActor(memberAccount)).thenReturn(false);
            when(authorizationService.isManager(memberAccount)).thenReturn(false);
            when(authorizationService.isStaff(memberAccount)).thenReturn(false);

            assertThatThrownBy(() -> locationService.listLocations(AUTH_HEADER))
                    .isInstanceOf(WorkshopForbiddenException.class)
                    .hasMessageContaining("Actor cannot access workshop locations");
        }
    }

    @Nested
    @DisplayName("Location Creation Tests")
    class CreateLocationTests {

        @Test
        @DisplayName("All-location actor (Admin) successfully creates a new location")
        void adminCreatesLocationSuccessfully() {
            LocationRequest request = LocationRequest.builder()
                    .code("LOC-03")
                    .name("District 3 Workshop")
                    .address("789 Vo Van Tan, D3, HCMC")
                    .active(true)
                    .build();

            when(authorizationService.requireAllBranchActor(AUTH_HEADER)).thenReturn(adminAccount);
            when(locationRepository.findByCode("LOC-03")).thenReturn(Optional.empty());
            when(locationRepository.save(any(WorkshopLocation.class))).thenAnswer(invocation -> {
                WorkshopLocation b = invocation.getArgument(0);
                b.setId(3L);
                return b;
            });

            LocationResponse response = locationService.createLocation(AUTH_HEADER, request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(3L);
            assertThat(response.getCode()).isEqualTo("LOC-03");
            assertThat(response.getName()).isEqualTo("District 3 Workshop");
            verify(locationRepository).save(any(WorkshopLocation.class));
        }

        @Test
        @DisplayName("Duplicate location code is rejected with WorkshopValidationException")
        void duplicateLocationCodeRejected() {
            LocationRequest request = LocationRequest.builder()
                    .code("LOC-01")
                    .name("Duplicate Location")
                    .address("Some Address")
                    .build();

            when(authorizationService.requireAllBranchActor(AUTH_HEADER)).thenReturn(adminAccount);
            when(locationRepository.findByCode("LOC-01")).thenReturn(Optional.of(location1));

            assertThatThrownBy(() -> locationService.createLocation(AUTH_HEADER, request))
                    .isInstanceOf(WorkshopValidationException.class)
                    .hasMessageContaining("already exists");

            verify(locationRepository, never()).save(any());
        }
    }
}
