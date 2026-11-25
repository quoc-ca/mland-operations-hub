package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.AvailabilityGuardPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.GroupedCheckoutRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCashCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCounterCashFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** FR-08-045 (T089): only branch Staff confirm counter cash; a Manager may not. */
class RetailCashCollectionAuthorizationTest {
    private final GroupedCheckoutRepository checkouts = mock(GroupedCheckoutRepository.class);
    private final RetailOrderRepository orders = mock(RetailOrderRepository.class);
    private final RetailCashCollectionRepository collections = mock(RetailCashCollectionRepository.class);
    private final RetailCashCollectionService service = new RetailCashCollectionService(checkouts, orders, collections,
        mock(RetailVariantHoldRepository.class), mock(RetailVariantHoldLifecycleService.class),
        mock(CatalogueSalesFacade.class), mock(AvailabilityGuardPort.class), mock(LoyaltyFacade.class),
        mock(RetailBranchPartService.class));

    private static CurrentAccountActor actor(ActorRole role) {
        return new CurrentAccountActor(10L, role, ActorStatus.ACTIVE, true, List.of(5L));
    }

    @Test
    void managerOfTheBranchCannotConfirmCounterCash() {
        assertThatThrownBy(() -> service.confirm(actor(ActorRole.MANAGER), "order-1", "2000", "key-1"))
            .isInstanceOf(RetailAccessDeniedException.class)
            .hasMessage("Only active Staff can confirm counter cash");
        verifyNoInteractions(checkouts, orders, collections);
    }

    @Test
    void managerIsRefusedWith403ThroughTheFacade() {
        RetailOrder order = mock(RetailOrder.class);
        when(order.getPayableAmount()).thenReturn(BigDecimal.valueOf(2_000));
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.collectExactAmount(actor(ActorRole.MANAGER), "order-1", "key-1"))
            .isInstanceOfSatisfying(RetailCounterCashFacade.Refused.class, refused -> assertThat(refused.status()).isEqualTo(403));
    }

    @Test
    void staffOfTheBranchPassesTheRoleCheck() {
        when(collections.findByConfirmedByAccountIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(orders.findById("order-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(actor(ActorRole.STAFF), "order-1", "2000", "key-1"))
            .isInstanceOf(RetailNotFoundException.class);
    }

    @Test
    void inactiveStaffAndAnonymousAreRefused() {
        var suspended = new CurrentAccountActor(10L, ActorRole.STAFF, ActorStatus.SUSPENDED, true, List.of(5L));
        assertThatThrownBy(() -> service.confirm(suspended, "order-1", "2000", "key-1"))
            .isInstanceOf(RetailAccessDeniedException.class);
        assertThatThrownBy(() -> service.confirm(null, "order-1", "2000", "key-1"))
            .isInstanceOf(RetailUnauthorizedException.class);
    }
}
