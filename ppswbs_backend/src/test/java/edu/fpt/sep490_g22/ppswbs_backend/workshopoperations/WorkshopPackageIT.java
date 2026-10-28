package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSlotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageLifecycleTargetState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSlot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkshopPackageIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private WorkshopOperationsFacade operationsFacade;
    @Autowired private WorkshopLocationRepository locationRepository;
    @Autowired private WorkshopPackageRepository packageRepository;
    @Autowired private WorkshopSessionRepository sessionRepository;
    @Autowired private WorkshopSlotRepository slotRepository;
    @Autowired private WorkshopPackageService packageService;
    @Autowired private WorkshopPackageAuthorizationService packageAuthorization;

    @MockitoBean private AccountFacade accountFacade;
    @MockitoBean private WorkshopOperationsAuthorizationService authenticationService;

    @BeforeEach
    void requireRealMySql() throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"),
                    "WorkshopPackageIT requires real MySQL/Flyway evidence");
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '21' and success = 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void centralManagerMustCoverEveryActiveCanonicalLocation() {
        WorkshopLocation first = location("PKG-CENTRAL-A", true);
        WorkshopLocation second = location("PKG-CENTRAL-B", true);
        location("PKG-CENTRAL-INACTIVE", false);
        Long managerId = 73001L;
        when(authenticationService.authenticate("Bearer test-manager"))
                .thenReturn(new AccountSummary(managerId, ActorRole.MANAGER, ActorStatus.ACTIVE,
                        "internal-test@example.invalid", Instant.EPOCH, Instant.EPOCH));
        List<Long> everyActiveLocationId = locationRepository.findByActiveTrue().stream()
                .map(WorkshopLocation::getId).toList();
        when(accountFacade.getTargetAccount(managerId)).thenReturn(Optional.of(
                new TargetAccountSnapshot(managerId, ActorRole.MANAGER, ActorStatus.ACTIVE, everyActiveLocationId)));

        assertThat(packageAuthorization.requireCentralManager("Bearer test-manager")).isEqualTo(managerId);

        when(accountFacade.getTargetAccount(managerId)).thenReturn(Optional.of(
                new TargetAccountSnapshot(managerId, ActorRole.MANAGER, ActorStatus.ACTIVE, List.of(first.getId()))));
        assertThatThrownBy(() -> packageAuthorization.requireCentralManager("Bearer test-manager"))
                .isInstanceOf(WorkshopForbiddenException.class);
    }

    @Test
    void packageLifecycleControlsPublicVisibilityAndRetainsPublishedHistory() {
        PackageRequest request = packageRequest("Basic Ring", 1, 10, 1, 0);
        var draft = packageService.createDraft(request, 73002L, "pkg-create-" + UUID.randomUUID());
        assertThat(packageService.listPublished()).noneMatch(item -> item.getPackageId().equals(draft.getPackageId()));

        var published = packageService.transition(draft.getPackageId(), PackageLifecycleTargetState.PUBLISHED,
                73002L, "pkg-publish-" + UUID.randomUUID());
        assertThat(packageService.listPublished()).anyMatch(item -> item.getPackageId().equals(draft.getPackageId()));

        PackageRequest prospective = packageRequest("Basic Ring Updated", 1, 8, 1, 0);
        packageService.updateProspectively(published.getPackageId(), prospective, 73002L,
                "pkg-update-" + UUID.randomUUID());
        var retired = packageService.transition(published.getPackageId(), PackageLifecycleTargetState.RETIRED,
                73002L, "pkg-retire-" + UUID.randomUUID());

        assertThat(retired.getState().name()).isEqualTo("RETIRED");
        assertThat(packageRepository.findById(published.getPackageId())).isPresent();
        assertThat(packageService.listPublished()).noneMatch(item -> item.getPackageId().equals(published.getPackageId()));
    }

    @Test
    void basicRingAndSilverclayRemainGenericPublishedConfiguration() {
        WorkshopPackage basic = createPackage("Basic Ring", 1, 10, 1, 0, WorkshopPackageState.PUBLISHED);
        WorkshopPackage silverclay = createPackage("Silverclay", 2, 8, 2, 1, WorkshopPackageState.PUBLISHED);

        var basicSnapshot = operationsFacade.getPublishedPackageSelection(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery(
                        basic.getId().toString()));
        var silverclaySnapshot = operationsFacade.getPublishedPackageSelection(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery(
                        silverclay.getId().toString()));

        assertThat(basicSnapshot.requiredSessionCount()).isEqualTo(1);
        assertThat(basicSnapshot.sameLocationRequired()).isTrue();
        assertThat(basicSnapshot.minimumSameDaySlotGap()).isZero();
        assertThat(silverclaySnapshot.requiredSessionCount()).isEqualTo(2);
        assertThat(silverclaySnapshot.sameLocationRequired()).isTrue();
        assertThat(silverclaySnapshot.minimumSameDaySlotGap()).isEqualTo(1);
    }

    @Test
    void guestCanBrowsePublishedPackagesAndOnlyCustomerSafeSessionAvailability() throws Exception {
        WorkshopPackage published = createPackage("Public Package", 1, 4, 1, 0, WorkshopPackageState.PUBLISHED);
        WorkshopPackage draft = createPackage("Private Draft", 1, 4, 1, 0, WorkshopPackageState.DRAFT);
        WorkshopLocation location = location("PKG-PUBLIC", true);
        WorkshopSession selectable = createSession(location, 4, 2, LocalDate.now(ZONE).plusDays(9));
        WorkshopSession full = createSession(location, 5, 1, LocalDate.now(ZONE).plusDays(9));
        assertThat(reserve(published, selectable).status().name()).isEqualTo("ACCEPTED");
        assertThat(reserve(published, full).status().name()).isEqualTo("ACCEPTED");

        mockMvc.perform(get("/api/v1/workshop-packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.packageId == " + published.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.packageId == " + draft.getId() + ")]").doesNotExist());

        String body = mockMvc.perform(get("/api/v1/workshop-packages/{packageId}/sessions", published.getId())
                        .queryParam("locationId", location.getId().toString())
                        .queryParam("from", LocalDate.now(ZONE).toString())
                        .queryParam("to", LocalDate.now(ZONE).plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionReference").value(selectable.getReference()))
                .andExpect(jsonPath("$[0].locationId").value(location.getId()))
                .andExpect(jsonPath("$[0].customerSelectable").value(true))
                .andExpect(jsonPath("$[0].saleState").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].remainingCapacity").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("ticket", "booking", "staff", "audit", "email", "phone", "entity");
        assertThat(body).doesNotContain(full.getReference());
    }

    private CapacityHoldOutcome reserve(WorkshopPackage workshopPackage, WorkshopSession session) {
        return operationsFacade.reserveBookingCapacity(new ReserveBookingCapacityCommand(
                "pkg-public-booking-" + UUID.randomUUID(), workshopPackage.getId().toString(),
                List.of(UUID.fromString(session.getReference())), 1,
                UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    }

    private WorkshopPackage createPackage(String name, int minParticipants, int maxParticipants,
                                          int requiredSessionCount, int gap, WorkshopPackageState state) {
        return packageRepository.save(WorkshopPackage.builder().name(name + " " + UUID.randomUUID())
                .description("Customer-visible generic package fixture").publicPrice(450_000L)
                .minParticipants(minParticipants).maxParticipants(maxParticipants).requiredSessionCount(requiredSessionCount)
                .sameLocationRequired(true).minimumSameDaySlotGap(gap).state(state).build());
    }

    private WorkshopLocation location(String prefix, boolean active) {
        return locationRepository.saveAndFlush(WorkshopLocation.builder()
                .code(prefix + "-" + UUID.randomUUID()).displayName("Package IT Location")
                .addressSnapshot("test").active(active).build());
    }

    private WorkshopSession createSession(WorkshopLocation location, int slotNumber, int capacity, LocalDate date) {
        LocalTime start = LocalTime.of(slotNumber + 7, 0);
        WorkshopSlot slot = slotRepository.saveAndFlush(WorkshopSlot.builder()
                .reference(UUID.randomUUID().toString()).location(location).label("Package IT slot")
                .startTime(start).endTime(start.plusMinutes(45)).defaultCapacity(capacity).active(true).build());
        Instant startAt = date.atTime(start).atZone(ZONE).toInstant();
        return sessionRepository.saveAndFlush(WorkshopSession.builder().reference(UUID.randomUUID().toString())
                .location(location).slot(slot).workshopDate(date).scheduledStartAt(startAt)
                .scheduledEndAt(startAt.plusSeconds(2700)).checkInOpensAt(startAt.minusSeconds(1800))
                .capacity(capacity).state(SessionState.PUBLISHED).build());
    }

    private static PackageRequest packageRequest(String name, int min, int max, int required, int gap) {
        return PackageRequest.builder().name(name).description("Package administration integration fixture")
                .publicPrice(450_000L).minParticipants(min).maxParticipants(max).requiredSessionCount(required)
                .sameLocationRequired(true).minimumSameDaySlotGap(gap).build();
    }
}
