package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

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
    private edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository createAuditRepository;

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
        @DisplayName("Admin is denied listing locations (T129, FR-012c: no Admin business authority)")
        void adminDeniedFromListingLocations() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(adminAccount);

            assertThatThrownBy(() -> locationService.listLocations(AUTH_HEADER))
                    .isInstanceOf(WorkshopForbiddenException.class);
            verify(locationRepository, never()).findByActiveTrue();
        }

        @Test
        @DisplayName("Manager lists every location (inactive included) regardless of location assignments")
        void managerListsAllLocationsGlobally() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(locationRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(location1, location2));

            List<LocationResponse> responses = locationService.listLocations(AUTH_HEADER);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getCode()).isEqualTo("LOC-01");
            assertThat(responses.get(0).getId()).isEqualTo(1L);
            verify(locationRepository).findAll(any(org.springframework.data.domain.Sort.class));
            verify(locationRepository, never()).findByIdInAndActiveTrue(any());
        }

        @Test
        @DisplayName("Manager with empty location assignments still receives every location")
        void managerWithEmptyScopeReceivesAllLocations() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(managerAccountA);
            when(authorizationService.isManager(managerAccountA)).thenReturn(true);
            when(locationRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(location1, location2));

            List<LocationResponse> responses = locationService.listLocations(AUTH_HEADER);

            assertThat(responses).hasSize(2);
            verify(locationRepository).findAll(any(org.springframework.data.domain.Sort.class));
        }

        @Test
        @DisplayName("Member role is denied with WorkshopForbiddenException when listing locations")
        void memberDeniedFromListingLocations() {
            when(authorizationService.authenticate(AUTH_HEADER)).thenReturn(memberAccount);
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
        @DisplayName("Manager successfully creates a new location")
        void managerCreatesLocationSuccessfully() {
            LocationRequest request = LocationRequest.builder()
                    .code("LOC-03")
                    .name("District 3 Workshop")
                    .address("789 Vo Van Tan, D3, HCMC")
                    .active(true)
                    .build();

            when(authorizationService.requireGlobalManager(AUTH_HEADER)).thenReturn(managerAccountA);
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

            when(authorizationService.requireGlobalManager(AUTH_HEADER)).thenReturn(managerAccountA);
            when(locationRepository.findByCode("LOC-01")).thenReturn(Optional.of(location1));

            assertThatThrownBy(() -> locationService.createLocation(AUTH_HEADER, request))
                    .isInstanceOf(WorkshopValidationException.class)
                    .hasMessageContaining("already exists");

            verify(locationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("T135 updateLocation")
    class UpdateLocationTests {

        @Mock
        private edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository sessionRepository;
        @Mock
        private edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository auditEventRepository;

        private WorkshopLocationService service() {
            return new WorkshopLocationService(locationRepository, authorizationService, null, sessionRepository, auditEventRepository);
        }

        private void manager() {
            when(authorizationService.requireGlobalManager(AUTH_HEADER)).thenReturn(managerAccountA);
            lenient().when(locationRepository.findById(1L)).thenReturn(Optional.of(location1));
            lenient().when(locationRepository.save(any(WorkshopLocation.class))).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("non-Manager is refused before any lookup")
        void nonManagerRefused() {
            when(authorizationService.requireGlobalManager(AUTH_HEADER)).thenThrow(new WorkshopForbiddenException("no"));
            assertThatThrownBy(() -> service().updateLocation(AUTH_HEADER, 1L,
                    edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationUpdateRequest.builder().name("X").build()))
                    .isInstanceOf(WorkshopForbiddenException.class);
            verify(locationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("empty request, blank name and different code are validation errors; nothing saved")
        void validation() {
            manager();
            var update = edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationUpdateRequest.builder();
            assertThatThrownBy(() -> service().updateLocation(AUTH_HEADER, 1L, update.build()))
                    .isInstanceOf(WorkshopValidationException.class);
            assertThatThrownBy(() -> service().updateLocation(AUTH_HEADER, 1L, update.name(" ").build()))
                    .isInstanceOf(WorkshopValidationException.class);
            assertThatThrownBy(() -> service().updateLocation(AUTH_HEADER, 1L, update.name("Ok").code("OTHER").build()))
                    .isInstanceOf(WorkshopValidationException.class).hasMessageContaining("immutable");
            verify(locationRepository, never()).save(any());
            verifyNoInteractions(auditEventRepository);
        }

        @Test
        @DisplayName("unknown id is WorkshopNotFoundException")
        void unknownId() {
            manager();
            assertThatThrownBy(() -> service().updateLocation(AUTH_HEADER, 77L,
                    edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationUpdateRequest.builder().name("X").build()))
                    .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopNotFoundException.class);
        }

        @Test
        @DisplayName("deactivation is refused with WorkshopLifecycleException while a not-ended session uses the location")
        void deactivationRefused() {
            manager();
            when(sessionRepository.existsByLocationIdAndStateNotIn(eq(1L), any())).thenReturn(true);
            assertThatThrownBy(() -> service().updateLocation(AUTH_HEADER, 1L,
                    edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationUpdateRequest.builder().active(false).build()))
                    .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException.class);
            assertThat(location1.isActive()).isTrue();
            verify(locationRepository, never()).save(any());
            verifyNoInteractions(auditEventRepository);
        }

        @Test
        @DisplayName("an unchanged value writes no audit event; a rename writes exactly one")
        void auditPerAppliedChange() {
            manager();
            var same = edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationUpdateRequest.builder()
                    .name("Location One").address("123 Street 1").active(true).build();
            service().updateLocation(AUTH_HEADER, 1L, same);
            verifyNoInteractions(auditEventRepository);

            LocationResponse response = service().updateLocation(AUTH_HEADER, 1L,
                    edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.LocationUpdateRequest.builder().name(" Renamed ").build());
            assertThat(response.getName()).isEqualTo("Renamed");
            verify(auditEventRepository, times(1)).save(any());
        }
    }
}
