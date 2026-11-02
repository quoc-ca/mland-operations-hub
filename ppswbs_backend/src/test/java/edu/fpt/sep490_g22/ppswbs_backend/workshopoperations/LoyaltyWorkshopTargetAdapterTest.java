package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopPackageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.LoyaltyWorkshopTargetAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LoyaltyWorkshopTargetAdapterTest {

    private final WorkshopPackageRepository packages = mock(WorkshopPackageRepository.class);
    private final WorkshopLocationRepository locations = mock(WorkshopLocationRepository.class);
    private final WorkshopSessionRepository sessions = mock(WorkshopSessionRepository.class);
    private final LoyaltyWorkshopTargetAdapter adapter =
            new LoyaltyWorkshopTargetAdapter(packages, locations, sessions);

    @Test
    void supportsOnlyWorkshopScope() {
        assertThat(adapter.supports(TargetScope.WORKSHOP)).isTrue();
        assertThat(adapter.supports(TargetScope.PRODUCT)).isFalse();
    }

    @Test
    void returnsOneImmutableFactPerRequestedPackageBranchDateAndSession() {
        UUID sessionReference = UUID.randomUUID();
        WorkshopPackage workshopPackage = WorkshopPackage.builder()
                .id(42L).name("Pottery").state(WorkshopPackageState.DRAFT).build();
        WorkshopLocation location = WorkshopLocation.builder()
                .id(7L).code("LOC-7").active(true).build();
        WorkshopSession session = WorkshopSession.builder()
                .id(99L).reference(sessionReference.toString()).state(SessionState.PUBLISHED)
                .location(location).build();
        when(packages.findById(42L)).thenReturn(Optional.of(workshopPackage));
        when(locations.findById(7L)).thenReturn(Optional.of(location));
        when(sessions.findByReference(sessionReference.toString())).thenReturn(Optional.of(session));
        List<TargetRef> targets = List.of(
                new TargetRef(TargetDimension.PACKAGE, "42"),
                new TargetRef(TargetDimension.BRANCH, "7"),
                new TargetRef(TargetDimension.WORKSHOP_DATE, "2026-11-10"),
                new TargetRef(TargetDimension.SESSION, sessionReference.toString()));

        List<TargetFact> facts = adapter.validateAndGuard(TargetScope.WORKSHOP, targets);

        assertThat(facts).hasSize(4);
        assertThat(facts.get(0)).isEqualTo(new TargetFact(TargetDimension.PACKAGE, "42", true, true,
                false, false, false));
        assertThat(facts.get(1)).isEqualTo(new TargetFact(TargetDimension.BRANCH, "7", true, true,
                false, true, true));
        assertThat(facts.get(2)).isEqualTo(new TargetFact(TargetDimension.WORKSHOP_DATE, "2026-11-10",
                true, true, false, true, true));
        assertThat(facts.get(3)).isEqualTo(new TargetFact(TargetDimension.SESSION, sessionReference.toString(),
                true, true, false, true, true));
        assertThatThrownBy(() -> facts.add(facts.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void malformedOrUnknownTargetsFailClosedAndNonCanonicalSessionIsNotLookedUp() {
        UUID sessionReference = UUID.randomUUID();
        String upperCaseReference = sessionReference.toString().toUpperCase();
        List<TargetFact> facts = adapter.validateAndGuard(TargetScope.WORKSHOP, List.of(
                new TargetRef(TargetDimension.PACKAGE, "042"),
                new TargetRef(TargetDimension.BRANCH, "999"),
                new TargetRef(TargetDimension.WORKSHOP_DATE, "2026-2-01"),
                new TargetRef(TargetDimension.SESSION, upperCaseReference)));

        assertThat(facts).allSatisfy(fact -> assertThat(fact.exists()).isFalse());
        verifyNoInteractions(packages);
        verify(locations).findById(999L);
        verifyNoInteractions(sessions);
    }

    @Test
    void repositoryFailureMapsToDependencyUnavailable() {
        when(packages.findById(42L)).thenThrow(new DataAccessResourceFailureException("unavailable"));

        assertThatThrownBy(() -> adapter.validateAndGuard(TargetScope.WORKSHOP,
                List.of(new TargetRef(TargetDimension.PACKAGE, "42"))))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        error -> assertThat(error.code()).isEqualTo(LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void rejectsWrongScopeAndUnsupportedTargetDimension() {
        assertThatThrownBy(() -> adapter.validateAndGuard(TargetScope.PRODUCT, List.of()))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        error -> assertThat(error.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> adapter.validateAndGuard(TargetScope.WORKSHOP,
                List.of(new TargetRef(TargetDimension.PRODUCT, "1"))))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        error -> assertThat(error.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT));
    }
}
