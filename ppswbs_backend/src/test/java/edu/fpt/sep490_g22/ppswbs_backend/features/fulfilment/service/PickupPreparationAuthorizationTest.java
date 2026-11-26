package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.dao.PickupEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.entity.PickupEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCounterCashFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailPickupOrderFacade;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C2 (Thinh, 2026-11-26, T090): Staff or a Manager of the branch packs a pickup order; only Staff hands it over. */
class PickupPreparationAuthorizationTest {
    private static final String ORDER = "order-1";
    private final RetailPickupOrderFacade orders = mock(RetailPickupOrderFacade.class);
    private final PickupEvidenceRepository evidence = mock(PickupEvidenceRepository.class);
    private final PickupOperationsService service = new PickupOperationsService(orders, evidence, mock(RetailCounterCashFacade.class));

    private static CurrentAccountActor actor(ActorRole role, ActorStatus status, Long location) {
        return new CurrentAccountActor(11L, role, status, true, List.of(location));
    }

    private static RetailPickupOrderFacade.PickupOrder order(String state, boolean ready) {
        return new RetailPickupOrderFacade.PickupOrder(ORDER, 5L, "PAID", "PICKUP", state, ready);
    }

    @Test
    void managerOfTheBranchPreparesAPickupOrder() {
        when(orders.lock(ORDER)).thenReturn(order("PAID", false));
        when(evidence.findByOrderIdAndMilestone(ORDER, "PREPARED")).thenReturn(Optional.empty());
        when(evidence.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.markPrepared(ORDER)).thenReturn(order("READY_FOR_PICKUP", true));

        var result = service.prepare(actor(ActorRole.MANAGER, ActorStatus.ACTIVE, 5L), ORDER);

        assertThat(result.milestone()).isEqualTo("PREPARED");
        assertThat(result.fulfilmentState()).isEqualTo("READY_FOR_PICKUP");
        verify(evidence).saveAndFlush(any(PickupEvidence.class));
    }

    @Test
    void managerOfAnotherBranchCannotPrepare() {
        when(orders.lock(ORDER)).thenReturn(order("PAID", false));

        assertThatThrownBy(() -> service.prepare(actor(ActorRole.MANAGER, ActorStatus.ACTIVE, 7L), ORDER))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
        verify(orders, never()).markPrepared(any());
    }

    @Test
    void suspendedManagerAndOtherRolesCannotPrepare() {
        for (var actor : List.of(actor(ActorRole.MANAGER, ActorStatus.SUSPENDED, 5L), actor(ActorRole.ADMIN, ActorStatus.ACTIVE, 5L),
                actor(ActorRole.MEMBER, ActorStatus.ACTIVE, 5L))) {
            assertThatThrownBy(() -> service.prepare(actor, ORDER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        }
        verify(orders, never()).lock(any());
    }

    @Test
    void managerCannotHandAPickupOrderOver() {
        assertThatThrownBy(() -> service.handOver(actor(ActorRole.MANAGER, ActorStatus.ACTIVE, 5L), ORDER))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
        verify(orders, never()).lock(any());
        verify(orders, never()).markHandedOver(any());
    }
}
