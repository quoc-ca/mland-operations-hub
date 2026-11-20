package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Session 2026-11-20: the simulated GHTK partner records only "Đã lấy hàng" and "Giao thành công". */
class CarrierShipmentServiceTest {

    private static final String SHIPMENT_ID = "ship-1";
    private final RetailShipmentRepository repository = mock(RetailShipmentRepository.class);
    private final CarrierShipmentService service = new CarrierShipmentService(repository);

    private static RetailShipment shipment(RetailShipmentStatus status) {
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.valueOf(200), BigDecimal.valueOf(30000), BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 5L, "GHTK", BigDecimal.valueOf(200), BigDecimal.valueOf(30000), BigDecimal.ZERO);
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", "order-1");
        var shipment = new RetailShipment(checkout, order, "S1234567890", "Nguyen A", "0900", "12 Addr",
            "Ha Noi", "Ba Dinh", "Lieu Giai");
        for (RetailShipmentStatus[] path = RetailShipmentStatus.values(); shipment.getStatus() != status;) {
            shipment.advanceTo(path[shipment.getStatus().ordinal() + 1], 99L, "STAFF#99", null);
        }
        return shipment;
    }

    @Test
    void staffMembersAndManagersCannotUseTheCourierPortal() {
        for (ActorRole role : List.of(ActorRole.MEMBER, ActorRole.STAFF, ActorRole.MANAGER, ActorRole.ADMIN)) {
            var actor = new CurrentAccountActor(7L, role, ActorStatus.ACTIVE, true, List.of(5L));
            assertThatThrownBy(() -> service.list(actor, null, 0, 20)).isInstanceOf(RetailAccessDeniedException.class);
            assertThatThrownBy(() -> service.transition(actor, SHIPMENT_ID, "PICKED_UP", 0))
                .isInstanceOf(RetailAccessDeniedException.class);
        }
        assertThatThrownBy(() -> service.list(null, null, 0, 20)).isInstanceOf(RetailUnauthorizedException.class);
        verify(repository, never()).findCarrierShipments(any(), any());
        verify(repository, never()).lockById(any());
    }

    @Test
    void theCourierCollectsAPackedOrderThenDeliversIt() {
        var shipment = shipment(RetailShipmentStatus.READY_FOR_CARRIER);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));

        var picked = service.record(SHIPMENT_ID, "PICKED_UP", shipment.getVersion(), 50L, "GHTK#50");
        assertThat(picked.status()).isEqualTo("HANDED_TO_CARRIER");
        assertThat(shipment.getBranchOrder().getFulfilmentState()).isEqualTo("HANDED_TO_CARRIER");

        var delivered = service.record(SHIPMENT_ID, "DELIVERED", shipment.getVersion(), 50L, "GHTK#50");
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(shipment.getBranchOrder().getFulfilmentState()).isEqualTo("DELIVERED");
        assertThat(shipment.getHistory()).extracting(entry -> entry.getActorLabel()).contains("GHTK#50");

        var view = CarrierShipmentService.view(shipment);
        assertThat(view.status()).isEqualTo("DELIVERED");
        assertThat(view.trackingNumber()).isEqualTo("S1234567890");
        assertThat(view.recipientPhone()).isEqualTo("0900");
        assertThat(view.shippingCharge()).isEqualTo("30000");
    }

    @Test
    void theCourierCannotCollectBeforePackingNorDeliverBeforeCollecting() {
        var preparing = shipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(preparing));
        assertThatThrownBy(() -> service.record(SHIPMENT_ID, "PICKED_UP", preparing.getVersion(), 50L, "GHTK#50"))
            .isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> service.record(SHIPMENT_ID, "DELIVERED", preparing.getVersion(), 50L, "GHTK#50"))
            .isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> service.record(SHIPMENT_ID, "PICKED_UP", preparing.getVersion() + 1, 50L, "GHTK#50"))
            .isInstanceOf(RetailConflictException.class);
        assertThat(preparing.getStatus()).isEqualTo(RetailShipmentStatus.PREPARING);
    }

    @Test
    void anActiveCarrierAccountMayUseThePortal() {
        assertThat(CarrierShipmentService.isActiveCarrier(
            new CurrentAccountActor(50L, ActorRole.CARRIER, ActorStatus.ACTIVE, true, List.of()))).isTrue();
        assertThat(CarrierShipmentService.isActiveCarrier(
            new CurrentAccountActor(null, ActorRole.CARRIER, ActorStatus.ACTIVE, true, List.of()))).isFalse();
    }
}
