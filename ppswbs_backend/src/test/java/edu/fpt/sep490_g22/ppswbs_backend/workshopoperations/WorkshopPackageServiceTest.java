package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageLifecycleTargetState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOperationsAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopPackageServiceTest {

    @Mock private AccountFacade accountFacade;
    @Mock private WorkshopOperationsAuthorizationService authenticationService;
    @Mock private WorkshopLocationRepository locationRepository;
    @Mock private WorkshopPackageRepository packageRepository;
    @Mock private OperationsAuditEventRepository auditRepository;

    private WorkshopPackageAuthorizationService authorization;
    private WorkshopPackageService packageService;

    @BeforeEach
    void setUp() {
        authorization = new WorkshopPackageAuthorizationService(accountFacade, authenticationService, locationRepository);
        packageService = new WorkshopPackageService(packageRepository, auditRepository, new ObjectMapper());
    }

    @Test
    void centralManagerMustBeActiveAndCoverEveryCurrentlyActiveLocation() {
        when(authenticationService.authenticate("Bearer manager-token"))
                .thenReturn(new AccountSummary(17L, ActorRole.MANAGER, ActorStatus.ACTIVE,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        when(accountFacade.getTargetAccount(17L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(17L, ActorRole.MANAGER, ActorStatus.ACTIVE, List.of(2L, 1L))));
        when(locationRepository.findByActiveTrue()).thenReturn(List.of(
                WorkshopLocation.builder().id(1L).build(), WorkshopLocation.builder().id(2L).build()));

        assertThat(authorization.requireCentralManager("Bearer manager-token")).isEqualTo(17L);
    }

    @Test
    void incompleteLocationCoverageOrNonManagerIsDenied() {
        when(authenticationService.authenticate("Bearer token"))
                .thenReturn(new AccountSummary(18L, ActorRole.MANAGER, ActorStatus.ACTIVE,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        when(accountFacade.getTargetAccount(18L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(18L, ActorRole.MANAGER, ActorStatus.ACTIVE, List.of(1L))));
        when(locationRepository.findByActiveTrue()).thenReturn(List.of(
                WorkshopLocation.builder().id(1L).build(), WorkshopLocation.builder().id(2L).build()));
        assertThatThrownBy(() -> authorization.requireCentralManager("Bearer token"))
                .isInstanceOf(WorkshopForbiddenException.class);

        when(authenticationService.authenticate("Bearer token"))
                .thenReturn(new AccountSummary(18L, ActorRole.STAFF, ActorStatus.ACTIVE,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        when(accountFacade.getTargetAccount(18L)).thenReturn(Optional.of(
                new TargetAccountSnapshot(18L, ActorRole.STAFF, ActorStatus.ACTIVE, List.of(1L, 2L))));
        assertThatThrownBy(() -> authorization.requireCentralManager("Bearer token"))
                .isInstanceOf(WorkshopForbiddenException.class);
    }

    @Test
    void packageEditsAreProspectiveAndLifecycleChangesAreAudited() {
        WorkshopPackage workshopPackage = packageEntity(31L, WorkshopPackageState.DRAFT);
        when(packageRepository.findByIdWithLock(31L)).thenReturn(Optional.of(workshopPackage));
        when(packageRepository.save(any(WorkshopPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = packageService.updateProspectively(31L, request(700_000L), 17L, "corr-update");
        assertThat(updated.getPublicPrice()).isEqualTo(700_000L);
        assertThat(updated.getState().name()).isEqualTo("DRAFT");

        var published = packageService.transition(31L, PackageLifecycleTargetState.PUBLISHED, 17L, "corr-publish");
        assertThat(published.getState().name()).isEqualTo("PUBLISHED");
        assertThat(workshopPackage.getState()).isEqualTo(WorkshopPackageState.PUBLISHED);

        ArgumentCaptor<OperationsAuditEvent> auditCaptor = ArgumentCaptor.forClass(OperationsAuditEvent.class);
        verify(auditRepository, org.mockito.Mockito.times(2)).save(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues()).allSatisfy(audit -> {
            assertThat(audit.getActorId()).isEqualTo(17L);
            assertThat(audit.getCorrelationId()).isNotBlank();
            assertThat(audit.getOutcome()).isEqualTo("SUCCESS");
            assertThat(audit.getMetadata()).contains("packageId", "31").doesNotContain("private@example.test");
        });

        assertThatThrownBy(() -> packageService.transition(31L, PackageLifecycleTargetState.PUBLISHED, 17L, "corr-again"))
                .isInstanceOf(WorkshopLifecycleException.class);
    }

    @Test
    void creationStartsDraftAndPublishedPackageCanOnlyRetire() {
        when(packageRepository.save(any(WorkshopPackage.class))).thenAnswer(invocation -> {
            WorkshopPackage entity = invocation.getArgument(0);
            entity.setId(44L);
            return entity;
        });
        var created = packageService.createDraft(request(500_000L), 17L, "corr-create");
        assertThat(created.getPackageId()).isEqualTo(44L);
        assertThat(created.getState().name()).isEqualTo("DRAFT");

        WorkshopPackage published = packageEntity(44L, WorkshopPackageState.PUBLISHED);
        when(packageRepository.findByIdWithLock(44L)).thenReturn(Optional.of(published));
        var retired = packageService.transition(44L, PackageLifecycleTargetState.RETIRED, 17L, "corr-retire");
        assertThat(retired.getState().name()).isEqualTo("RETIRED");
        assertThat(published.getState()).isEqualTo(WorkshopPackageState.RETIRED);
    }

    private static WorkshopPackage packageEntity(Long id, WorkshopPackageState state) {
        return WorkshopPackage.builder().id(id).name("Basic Ring").description("Test")
                .publicPrice(500_000L).minParticipants(1).maxParticipants(10).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0).state(state).build();
    }

    private static PackageRequest request(Long price) {
        return PackageRequest.builder().name("Basic Ring").description("Test")
                .publicPrice(price).minParticipants(1).maxParticipants(10).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0).build();
    }
}
