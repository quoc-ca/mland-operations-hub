package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CarrierShipmentDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOrderShipmentServiceTest {

    @Mock
    private CustomOrderShipmentRepository shipmentRepository;

    @Mock
    private CustomOrderRepository customOrderRepository;

    private CustomOrderShipmentService shipmentService;

    private CustomOrder testOrder;
    private final String orderId = "order-uuid-101";
    private final String orderRef = "CO-AABBCCDD";
    private final Long memberId = 202L;

    @BeforeEach
    void setUp() {
        shipmentService = new CustomOrderShipmentService(
                shipmentRepository,
                customOrderRepository
        );

        testOrder = CustomOrder.builder()
                .id(orderId)
                .customOrderReference(orderRef)
                .memberAccountId(memberId)
                .shippingAddress("123 Le Loi, Da Nang")
                .status("READY_FOR_FULFILMENT")
                .productionStatus("COMPLETED")
                .fulfilmentStatus("READY_FOR_FULFILMENT")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("createShipmentForOrder generates S-prefixed 10-digit tracking number and creates shipment")
    void createShipmentForOrder_Success_GeneratesTrackingNumberAndSaves() {
        when(shipmentRepository.findTopByCustomOrderReferenceOrderByCreatedAtDesc(orderRef)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(CustomOrderShipment.class))).thenAnswer(inv -> {
            CustomOrderShipment s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        CustomOrderShipment result = shipmentService.createShipmentForOrder(testOrder);

        assertThat(result).isNotNull();
        assertThat(result.getTrackingNumber()).matches("^S\\d{10}$");
        assertThat(result.getCarrierService()).isEqualTo("GHTK");
        assertThat(result.getStatus()).isEqualTo("PREPARING");
        assertThat(result.getShippingAddress()).isEqualTo("123 Le Loi, Da Nang");
        assertThat(result.getRecipientName()).contains(orderRef);
        verify(shipmentRepository).save(any(CustomOrderShipment.class));
    }

    @Test
    @DisplayName("createShipmentForOrder returns existing shipment if already created")
    void createShipmentForOrder_AlreadyExists_ReturnsExisting() {
        CustomOrderShipment existing = CustomOrderShipment.builder()
                .id(1L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S1234567890")
                .status("PREPARING")
                .build();
        when(shipmentRepository.findTopByCustomOrderReferenceOrderByCreatedAtDesc(orderRef)).thenReturn(Optional.of(existing));

        CustomOrderShipment result = shipmentService.createShipmentForOrder(testOrder);

        assertThat(result).isSameAs(existing);
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("staffAdvanceToReadyForCarrier advances shipment status to READY_FOR_CARRIER")
    void staffAdvanceToReadyForCarrier_Success() {
        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .id(1L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S1234567890")
                .status("PREPARING")
                .version(1)
                .build();
        when(shipmentRepository.findTopByCustomOrderReferenceOrderByCreatedAtDesc(orderRef)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(CustomOrderShipment.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomOrderShipment updated = shipmentService.staffAdvanceToReadyForCarrier(orderRef);

        assertThat(updated.getStatus()).isEqualTo("READY_FOR_CARRIER");
        verify(shipmentRepository).save(shipment);
    }

    @Test
    @DisplayName("staffAdvanceToReadyForCarrier returns shipment unchanged if already at READY_FOR_CARRIER")
    void staffAdvanceToReadyForCarrier_AlreadyAdvanced_ReturnsUnchanged() {
        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .id(1L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S1234567890")
                .status("READY_FOR_CARRIER")
                .version(1)
                .build();
        when(shipmentRepository.findTopByCustomOrderReferenceOrderByCreatedAtDesc(orderRef)).thenReturn(Optional.of(shipment));

        CustomOrderShipment result = shipmentService.staffAdvanceToReadyForCarrier(orderRef);

        assertThat(result.getStatus()).isEqualTo("READY_FOR_CARRIER");
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("transitionCustomShipment handles PICKED_UP action, updates status to HANDED_TO_CARRIER")
    void transitionCustomShipment_PickedUp_Success() {
        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .id(1L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S1234567890")
                .status("READY_FOR_CARRIER")
                .version(1)
                .build();
        when(shipmentRepository.lockById(1L)).thenReturn(Optional.of(shipment));

        var result = shipmentService.transitionCustomShipment("CUST_SHIP_1", "PICKED_UP", 1);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("HANDED_TO_CARRIER");
        assertThat(shipment.getStatus()).isEqualTo("HANDED_TO_CARRIER");
        assertThat(shipment.getPickedUpAt()).isNotNull();
        verify(shipmentRepository).saveAndFlush(shipment);
    }

    @Test
    @DisplayName("transitionCustomShipment handles DELIVERED action, marks order CLOSED and fulfilment DELIVERED")
    void transitionCustomShipment_Delivered_Success() {
        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .id(1L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S1234567890")
                .status("HANDED_TO_CARRIER")
                .version(2)
                .build();
        when(shipmentRepository.lockById(1L)).thenReturn(Optional.of(shipment));
        when(customOrderRepository.findByCustomOrderReference(orderRef)).thenReturn(Optional.of(testOrder));

        var result = shipmentService.transitionCustomShipment("CUST_SHIP_1", "DELIVERED", 2);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("DELIVERED");
        assertThat(shipment.getStatus()).isEqualTo("DELIVERED");
        assertThat(shipment.getDeliveredAt()).isNotNull();
        assertThat(testOrder.getStatus()).isEqualTo("CLOSED");
        assertThat(testOrder.getFulfilmentStatus()).isEqualTo("DELIVERED");
        verify(customOrderRepository).save(testOrder);
    }

    @Test
    @DisplayName("transitionCustomShipment throws IllegalStateException on optimistic lock version mismatch")
    void transitionCustomShipment_VersionMismatch_ThrowsIllegalState() {
        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .id(1L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S1234567890")
                .status("READY_FOR_CARRIER")
                .version(5)
                .build();
        when(shipmentRepository.lockById(1L)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.transitionCustomShipment("CUST_SHIP_1", "PICKED_UP", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đơn vừa được cập nhật");
    }

    @Test
    @DisplayName("transitionCustomShipment throws IllegalStateException when picking up parcel not yet READY_FOR_CARRIER")
    void transitionCustomShipment_InvalidCurrentStatus_ThrowsIllegalState() {
        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .id(1L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S1234567890")
                .status("PREPARING")
                .version(1)
                .build();
        when(shipmentRepository.lockById(1L)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.transitionCustomShipment("CUST_SHIP_1", "PICKED_UP", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chỉ lấy được hàng khi xưởng đã chuẩn bị xong");
    }

    @Test
    @DisplayName("listForCarrier maps all custom shipments to CarrierShipmentDtos.Shipment with CUST_SHIP_ prefix")
    void listForCarrier_MapsCorrectly() {
        CustomOrderShipment shipment = CustomOrderShipment.builder()
                .id(42L)
                .customOrderId(orderId)
                .customOrderReference(orderRef)
                .trackingNumber("S9876543210")
                .status("READY_FOR_CARRIER")
                .shippingAddress("456 Tran Phu, Da Nang")
                .recipientName("Tran B")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(1)
                .build();
        when(shipmentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(shipment));

        List<CarrierShipmentDtos.Shipment> carrierList = shipmentService.listForCarrier();

        assertThat(carrierList).hasSize(1);
        CarrierShipmentDtos.Shipment item = carrierList.get(0);
        assertThat(item.id()).isEqualTo("CUST_SHIP_42");
        assertThat(item.trackingNumber()).isEqualTo("S9876543210");
        assertThat(item.orderCode()).isEqualTo(orderRef);
        assertThat(item.status()).isEqualTo("READY_FOR_CARRIER");
        assertThat(item.shippingCharge()).isEqualTo("Miễn phí");
    }
}
