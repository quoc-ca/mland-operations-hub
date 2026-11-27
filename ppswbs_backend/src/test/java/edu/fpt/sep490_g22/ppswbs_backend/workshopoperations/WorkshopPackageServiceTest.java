package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.TargetAccountSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SharedMediaException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SharedMediaFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageImageInvalidException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageImageNotFoundException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopPackageServiceTest {

    @Mock private AccountFacade accountFacade;
    @Mock private WorkshopOperationsAuthorizationService authenticationService;
    @Mock private WorkshopLocationRepository locationRepository;
    @Mock private WorkshopPackageRepository packageRepository;
    @Mock private OperationsAuditEventRepository auditRepository;
    @Mock private SharedMediaFacade sharedMedia;

    private WorkshopPackageAuthorizationService authorization;
    private WorkshopPackageService packageService;

    @BeforeEach
    void setUp() {
        authorization = new WorkshopPackageAuthorizationService(authenticationService);
        packageService = new WorkshopPackageService(packageRepository, auditRepository, new ObjectMapper(), sharedMedia);
    }

    @Test
    void anyActiveManagerMayManagePackagesRegardlessOfLocationAssignments() {
        when(authenticationService.authenticate("Bearer manager-token"))
                .thenReturn(new AccountSummary(17L, ActorRole.MANAGER, ActorStatus.ACTIVE,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        assertThat(authorization.requireActiveManager("Bearer manager-token")).isEqualTo(17L);
    }

    @Test
    void nonManagerIsDenied() {
        when(authenticationService.authenticate("Bearer token"))
                .thenReturn(new AccountSummary(18L, ActorRole.MANAGER, ActorStatus.ACTIVE,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        when(authenticationService.authenticate("Bearer token"))
                .thenReturn(new AccountSummary(18L, ActorRole.STAFF, ActorStatus.ACTIVE,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        assertThatThrownBy(() -> authorization.requireActiveManager("Bearer token"))
                .isInstanceOf(WorkshopForbiddenException.class);
    }

    @Test
    void adminIsDeniedBecausePackageManagementIsManagerOnly() {
        when(authenticationService.authenticate("Bearer admin-token"))
                .thenReturn(new AccountSummary(19L, ActorRole.ADMIN, ActorStatus.ACTIVE,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        assertThatThrownBy(() -> authorization.requireActiveManager("Bearer admin-token"))
                .isInstanceOf(WorkshopForbiddenException.class);
    }

    @Test
    void suspendedManagerIsDenied() {
        when(authenticationService.authenticate("Bearer suspended-token"))
                .thenReturn(new AccountSummary(20L, ActorRole.MANAGER, ActorStatus.SUSPENDED,
                        "private@example.test", Instant.EPOCH, Instant.EPOCH));
        assertThatThrownBy(() -> authorization.requireActiveManager("Bearer suspended-token"))
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

    @Test
    void retiredPackageCannotBeEdited() {
        WorkshopPackage retired = packageEntity(55L, WorkshopPackageState.RETIRED);
        when(packageRepository.findByIdWithLock(55L)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> packageService.updateProspectively(55L, request(700_000L), 17L, "corr-edit-retired"))
                .isInstanceOf(WorkshopLifecycleException.class)
                .hasMessageContaining("Retired Workshop Package cannot be edited");
    }

    @Test
    void managementListingIncludesAllLifecycleStatesAndSupportsStateFilter() {
        WorkshopPackage draft = packageEntity(61L, WorkshopPackageState.DRAFT);
        WorkshopPackage published = packageEntity(62L, WorkshopPackageState.PUBLISHED);
        WorkshopPackage retired = packageEntity(63L, WorkshopPackageState.RETIRED);
        when(packageRepository.findAllByOrderByIdAsc()).thenReturn(List.of(draft, published, retired));
        when(packageRepository.findByStateOrderByIdAsc(WorkshopPackageState.DRAFT)).thenReturn(List.of(draft));

        assertThat(packageService.listForManagement(null))
                .extracting(item -> item.getPackageId())
                .containsExactly(61L, 62L, 63L);
        assertThat(packageService.listForManagement(
                edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.WorkshopPackageState.DRAFT))
                .extracting(item -> item.getPackageId())
                .containsExactly(61L);
        verify(packageRepository).findAllByOrderByIdAsc();
        verify(packageRepository).findByStateOrderByIdAsc(WorkshopPackageState.DRAFT);
    }

    @Test
    void aNewCoverImageIsAttachedForTheActingManagerAndReturned() {
        when(packageRepository.save(any(WorkshopPackage.class))).thenAnswer(invocation -> {
            WorkshopPackage entity = invocation.getArgument(0);
            entity.setId(71L);
            return entity;
        });
        UUID image = UUID.randomUUID();

        var created = packageService.createDraft(request(500_000L).toBuilder().imageMediaId(image).build(), 17L, "c");

        assertThat(created.getImageMediaId()).isEqualTo(image);
        verify(sharedMedia).attachOwned("17", "workshopoperations", "package:71", image);
    }

    @Test
    void replacingReleasesThePreviousImageAndOmittingItRemovesIt() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        WorkshopPackage entity = packageEntity(72L, WorkshopPackageState.PUBLISHED);
        entity.setImageMediaId(first);
        when(packageRepository.findByIdWithLock(72L)).thenReturn(Optional.of(entity));
        when(packageRepository.save(any(WorkshopPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        packageService.updateProspectively(72L, request(1L).toBuilder().imageMediaId(first).build(), 18L, "c");
        verifyNoInteractions(sharedMedia);

        packageService.updateProspectively(72L, request(1L).toBuilder().imageMediaId(second).build(), 18L, "c");
        verify(sharedMedia).attachOwned("18", "workshopoperations", "package:72", second);
        verify(sharedMedia).release("workshopoperations", "package:72", first);

        var removed = packageService.updateProspectively(72L, request(1L), 18L, "c");
        assertThat(removed.getImageMediaId()).isNull();
        verify(sharedMedia).release("workshopoperations", "package:72", second);
    }

    @Test
    void anImageThatCannotBeUsedIsRefused() {
        UUID image = UUID.randomUUID();
        WorkshopPackage entity = packageEntity(73L, WorkshopPackageState.DRAFT);
        when(packageRepository.findByIdWithLock(73L)).thenReturn(Optional.of(entity));
        doThrow(new SharedMediaException(SharedMediaException.Reason.NOT_FOUND, null))
                .when(sharedMedia).attachOwned("17", "workshopoperations", "package:73", image);

        assertThatThrownBy(() -> packageService.updateProspectively(73L,
                request(1L).toBuilder().imageMediaId(image).build(), 17L, "c"))
                .isInstanceOf(WorkshopPackageImageInvalidException.class);
        assertThat(entity.getImageMediaId()).isNull();
        verify(packageRepository, never()).save(any(WorkshopPackage.class));
    }

    @Test
    void thePublicSeesTheImageOfAPublishedPackageOnly() {
        UUID image = UUID.randomUUID();
        WorkshopPackage draft = packageEntity(74L, WorkshopPackageState.DRAFT);
        draft.setImageMediaId(image);
        when(packageRepository.findById(74L)).thenReturn(Optional.of(draft));
        var content = new SharedMediaFacade.Content("image/png", java.io.InputStream.nullInputStream());
        when(sharedMedia.open("workshopoperations", "package:74", image)).thenReturn(content);

        assertThatThrownBy(() -> packageService.openPublishedImage(74L))
                .isInstanceOf(WorkshopPackageImageNotFoundException.class);
        assertThat(packageService.openManagedImage(74L)).isSameAs(content);

        draft.setState(WorkshopPackageState.PUBLISHED);
        assertThat(packageService.openPublishedImage(74L)).isSameAs(content);

        draft.setImageMediaId(null);
        assertThatThrownBy(() -> packageService.openManagedImage(74L))
                .isInstanceOf(WorkshopPackageImageNotFoundException.class);
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
