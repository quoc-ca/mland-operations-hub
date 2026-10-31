package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopAddOnRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnLifecycleTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnWrite;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOn;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopLifecycleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopAddOnServiceTest {
    @Mock WorkshopAddOnRepository addOns;
    @Mock WorkshopPackageRepository packages;
    @Mock WorkshopLocationRepository locations;
    @Mock OperationsAuditEventRepository audits;

    private WorkshopAddOnService service() {
        return new WorkshopAddOnService(addOns, packages, locations, audits, new ObjectMapper());
    }

    @Test
    void customerListRequiresPublishedPackageAndActiveLocationAndReturnsOpaqueReference() {
        when(packages.findById(10L)).thenReturn(Optional.of(workshopPackage(10L, WorkshopPackageState.PUBLISHED)));
        when(locations.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(location(20L)));
        when(addOns.findByPackageIdAndLocationIdAndStateOrderByDescriptionAsc(10L, 20L, WorkshopAddOnState.ACTIVE))
                .thenReturn(List.of(addOn(91L, "abc00000-0000-4000-8000-000000000001", 10L, 20L,
                        "Clay kit", 15_000L, WorkshopAddOnState.ACTIVE)));

        List<AddOnResponse> result = service().listCustomerSelectable(10L, 20L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().addOnReference()).isEqualTo("abc00000-0000-4000-8000-000000000001");
        assertThat(result.getFirst().priceVnd()).isEqualTo(15_000L);
        assertThat(result.getFirst().currencyCode()).isEqualTo("VND");
        assertThat(java.util.Arrays.stream(AddOnResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList()).doesNotContain("id");
        verify(addOns).findByPackageIdAndLocationIdAndStateOrderByDescriptionAsc(
                10L, 20L, WorkshopAddOnState.ACTIVE);
    }

    @Test
    void quoteSnapshotRequiresPublishedPackageActiveLocationAndExactActiveTuple() {
        when(packages.findById(10L)).thenReturn(Optional.of(workshopPackage(10L, WorkshopPackageState.PUBLISHED)));
        when(locations.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(location(20L)));
        when(addOns.findByReferenceAndPackageIdAndLocationIdAndState(
                "abc00000-0000-4000-8000-000000000001", 10L, 20L, WorkshopAddOnState.ACTIVE))
                .thenReturn(Optional.of(addOn(91L, "abc00000-0000-4000-8000-000000000001", 10L, 20L,
                        "Clay kit", 15_000L, WorkshopAddOnState.ACTIVE)));

        var snapshot = service().getEligibleSnapshot("10", 20L, "abc00000-0000-4000-8000-000000000001");

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.addOnReference()).isEqualTo("abc00000-0000-4000-8000-000000000001");
        assertThat(snapshot.amountVnd()).isEqualTo(15_000L);
        assertThat(snapshot.currencyCode()).isEqualTo("VND");
    }

    @Test
    void quoteSnapshotRejectsInvalidOrIneligibleScopeWithoutLookingUpAddOn() {
        when(packages.findById(10L)).thenReturn(Optional.of(workshopPackage(10L, WorkshopPackageState.DRAFT)));

        assertThat(service().getEligibleSnapshot("not-a-package-id", 20L, "not-a-uuid")).isNull();
        assertThat(service().getEligibleSnapshot("10", 20L, "abc00000-0000-4000-8000-000000000001")).isNull();

        verifyNoInteractions(addOns);
    }

    @Test
    void createsDraftWithOpaqueReferenceAndAuditsProspectivePrice() {
        when(packages.findById(10L)).thenReturn(Optional.of(workshopPackage(10L, WorkshopPackageState.DRAFT)));
        when(locations.findById(20L)).thenReturn(Optional.of(location(20L)));
        when(addOns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AddOnResponse created = service().createDraft(10L, 20L,
                new AddOnWrite(" Clay kit ", 15_000L), 7L, "CORR-1");

        assertThat(created.state()).isEqualTo(WorkshopAddOnState.DRAFT);
        assertThat(UUID.fromString(created.addOnReference())).isNotNull();
        assertThat(created.description()).isEqualTo("Clay kit");
        ArgumentCaptor<WorkshopAddOn> captor = ArgumentCaptor.forClass(WorkshopAddOn.class);
        verify(addOns).save(captor.capture());
        assertThat(captor.getValue().getPriceVnd()).isEqualTo(15_000L);
        verify(audits).save(argThat(audit -> audit.getAction().equals("WORKSHOP_ADDON_CREATED")));
    }

    @Test
    void lifecycleAllowsDraftActivationAndActiveRetirementButRejectsRetiredEditing() {
        WorkshopAddOn draft = addOn(91L, "abc00000-0000-4000-8000-000000000001", 10L, 20L,
                "Clay kit", 15_000L, WorkshopAddOnState.DRAFT);
        when(addOns.findByReferenceAndScopeWithLock(draft.getReference(), 10L, 20L)).thenReturn(Optional.of(draft));
        when(addOns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service().transition(10L, 20L, draft.getReference(),
                new AddOnLifecycleTransitionRequest(WorkshopAddOnState.ACTIVE), 7L, "CORR-2").state())
                .isEqualTo(WorkshopAddOnState.ACTIVE);
        assertThat(service().transition(10L, 20L, draft.getReference(),
                new AddOnLifecycleTransitionRequest(WorkshopAddOnState.RETIRED), 7L, "CORR-3").state())
                .isEqualTo(WorkshopAddOnState.RETIRED);

        assertThatThrownBy(() -> service().updateProspectively(10L, 20L, draft.getReference(),
                new AddOnWrite("Updated kit", 16_000L), 7L, "CORR-4"))
                .isInstanceOf(WorkshopLifecycleException.class);
    }

    private static WorkshopPackage workshopPackage(Long id, WorkshopPackageState state) {
        return WorkshopPackage.builder().id(id).state(state).build();
    }

    private static WorkshopLocation location(Long id) {
        return WorkshopLocation.builder().id(id).active(true).build();
    }

    private static WorkshopAddOn addOn(Long id, String reference, Long packageId, Long locationId,
                                       String description, Long price, WorkshopAddOnState state) {
        return WorkshopAddOn.builder().id(id).reference(reference).packageId(packageId).locationId(locationId)
                .description(description).priceVnd(price).state(state).build();
    }
}
