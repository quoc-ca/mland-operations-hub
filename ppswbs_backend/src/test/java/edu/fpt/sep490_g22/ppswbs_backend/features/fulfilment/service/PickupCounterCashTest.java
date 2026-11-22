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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cash at the counter: the customer pays and takes the order in one step (Session 2026-11-20). */
class PickupCounterCashTest {
    private static final String ORDER = "order-1";
    private final RetailPickupOrderFacade orders = mock(RetailPickupOrderFacade.class);
    private final PickupEvidenceRepository evidence = mock(PickupEvidenceRepository.class);
    private final RetailCounterCashFacade cash = mock(RetailCounterCashFacade.class);
    private final PickupOperationsService service = new PickupOperationsService(orders, evidence, cash);

    private static CurrentAccountActor staff() {
        return new CurrentAccountActor(10L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(5L));
    }

    private static RetailPickupOrderFacade.PickupOrder order(String state, boolean ready) {
        return new RetailPickupOrderFacade.PickupOrder(ORDER, 5L, "PAID", "PICKUP", state, ready);
    }

    @Test
    void collectingTheCashAlsoHandsTheOrderOver() {
        var prepared = new PickupEvidence(ORDER, 5L, 10L, "PREPARED", Instant.parse("2026-11-20T10:00:00Z"));
        when(orders.lock(ORDER)).thenReturn(order("PAID", false));
        when(evidence.findByOrderIdAndMilestone(ORDER, "PREPARED")).thenReturn(Optional.empty(), Optional.of(prepared));
        when(evidence.findByOrderIdAndMilestone(ORDER, "HANDED_OVER")).thenReturn(Optional.empty());
        when(evidence.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orders.markPrepared(ORDER)).thenReturn(order("READY_FOR_PICKUP", true));
        when(orders.markHandedOver(ORDER)).thenReturn(order("PICKED_UP", true));

        var result = service.collectCashAndHandOver(staff(), ORDER, "key-1");

        verify(cash).collectExactAmount(staff(), ORDER, "key-1");
        verify(orders).markPrepared(ORDER);
        verify(orders).markHandedOver(ORDER);
        assertThat(result.milestone()).isEqualTo("HANDED_OVER");
        assertThat(result.fulfilmentState()).isEqualTo("PICKED_UP");
    }

    @Test
    void aRefusedCashCollectionRecordsNothingElse() {
        when(cash.collectExactAmount(any(), any(), any()))
            .thenThrow(new RetailCounterCashFacade.Refused(409, "The pickup deadline has passed; the order is no longer held"));

        assertThatThrownBy(() -> service.collectCashAndHandOver(staff(), ORDER, "key-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
        verify(orders, never()).markPrepared(any());
        verify(orders, never()).markHandedOver(any());
        verify(evidence, never()).saveAndFlush(any());
    }

    @Test
    void onlyActiveStaffCanCollect() {
        var manager = new CurrentAccountActor(11L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(5L));
        assertThatThrownBy(() -> service.collectCashAndHandOver(manager, ORDER, "key-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
        verify(cash, never()).collectExactAmount(any(), any(), any());
    }
}
