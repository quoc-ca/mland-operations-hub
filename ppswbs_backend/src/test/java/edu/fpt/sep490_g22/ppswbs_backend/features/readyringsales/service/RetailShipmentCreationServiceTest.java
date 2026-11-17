package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetailShipmentCreationServiceTest {
    private final RetailShipmentRepository repository = mock(RetailShipmentRepository.class);
    private final RetailShipmentCreationService service = new RetailShipmentCreationService(repository);

    @Test void unpaidCheckoutCannotCreateAnyShipment() {
        assertThatThrownBy(() -> service.createShipmentsForPaidCheckout(checkout()))
            .isInstanceOf(RetailConflictException.class);
        verifyNoInteractions(repository);
    }

    @Test void paidGhtkOrderCreatesOneMockShipmentAndPickupDoesNot() {
        var checkout = checkout();
        var delivery = order(checkout, "GHTK", "delivery");
        delivery.setRecipientSnapshot("Nguyen Van A", "0901234567", "123 Duong A", "Ha Noi", "Cau Giay", "Dich Vong");
        order(checkout, "PICKUP", "pickup");
        checkout.markPaid();
        when(repository.findByBranchOrder_Id("delivery")).thenReturn(Optional.empty());
        service.createShipmentsForPaidCheckout(checkout);
        var captured = ArgumentCaptor.forClass(RetailShipment.class);
        verify(repository).save(captured.capture());
        var shipment = captured.getValue();
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.PREPARING);
        assertThat(shipment.getMockTrackingNumber()).matches("S[0-9]{10}");
        assertThat(shipment.getRecipientPhone()).isEqualTo("0901234567");
        assertThat(shipment.getHistory()).hasSize(1);
        verify(repository, never()).findByBranchOrder_Id("pickup");
    }

    @Test void duplicateCreationDoesNotCreateAnotherShipment() {
        var checkout = checkout();
        order(checkout, "GHTK", "delivery");
        checkout.markPaid();
        when(repository.findByBranchOrder_Id("delivery")).thenReturn(Optional.of(mock(RetailShipment.class)));
        service.createShipmentsForPaidCheckout(checkout);
        verify(repository, never()).save(any());
    }

    private GroupedCheckout checkout() {
        var checkout = new GroupedCheckout(1L, "key", 1, new BigDecimal("150000"), new BigDecimal("30000"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(checkout, "id", "checkout");
        return checkout;
    }
    private RetailOrder order(GroupedCheckout checkout, String method, String id) {
        var order = new RetailOrder(checkout, 1L, method, new BigDecimal("150000"), new BigDecimal("30000"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(order, "id", id);
        checkout.addOrder(order);
        return order;
    }
}
