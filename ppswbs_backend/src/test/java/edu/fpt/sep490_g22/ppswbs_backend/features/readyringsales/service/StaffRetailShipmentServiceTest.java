package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShipmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentTransitionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipment;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffRetailShipmentServiceTest {

    private static final String SHIPMENT_ID = "ship-1";
    private static final String BRANCH_ORDER_ID = "order-1";
    private static final List<Long> LOCATIONS = List.of(5L);

    private CurrentAccountActor activeStaff(long id) {
        return new CurrentAccountActor(id, ActorRole.STAFF, ActorStatus.ACTIVE, true, LOCATIONS);
    }

    private CurrentAccountActor activeManager(long id) {
        return new CurrentAccountActor(id, ActorRole.MANAGER, ActorStatus.ACTIVE, true, LOCATIONS);
    }

    private RetailShipment newShipment(RetailShipmentStatus status) {
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 5L, "GHTK", BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", BRANCH_ORDER_ID);
        var shipment = new RetailShipment(checkout, order, "S1234567890", "Nguyen A", "0900", "12 Addr",
            "Ha Noi", "Ba Dinh", "Lieu Giai");
        // Walk the shipment sequentially up to the requested status so tests can start mid-lifecycle.
        for (RetailShipmentStatus[] path = RetailShipmentStatus.values(); shipment.getStatus() != status;) {
            RetailShipmentStatus next = path[shipment.getStatus().ordinal() + 1];
            shipment.advanceTo(next, 99L, "STAFF#99", "auto");
        }
        return shipment;
    }

    @Test
    void listRequiresActiveStaffOrManager() {
        var repository = mock(RetailShipmentRepository.class);
        var service = new StaffRetailShipmentService(repository);
        var query = new StaffRetailShipmentSummary.ListQuery(0, 20, null, null);

        assertThatThrownBy(() -> service.list(null, query))
            .isInstanceOf(RetailUnauthorizedException.class);
        assertThatThrownBy(() -> service.list(
            new CurrentAccountActor(1L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, LOCATIONS), query))
            .isInstanceOf(RetailAccessDeniedException.class);
        assertThatThrownBy(() -> service.list(
            new CurrentAccountActor(1L, ActorRole.STAFF, ActorStatus.SUSPENDED, true, LOCATIONS), query))
            .isInstanceOf(RetailAccessDeniedException.class);
        assertThatThrownBy(() -> service.list(
            new CurrentAccountActor(1L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of()), query))
            .isInstanceOf(RetailAccessDeniedException.class);
        verify(repository, org.mockito.Mockito.never()).findStaffShipments(any(), any(), any());
    }

    @Test
    void listBoundedToActorLocationsAndStatusFilter() {
        var repository = mock(RetailShipmentRepository.class);
        when(repository.findStaffShipments(eq(LOCATIONS), eq(RetailShipmentStatus.IN_TRANSIT), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(newShipment(RetailShipmentStatus.HANDED_TO_CARRIER))));
        var service = new StaffRetailShipmentService(repository);
        var query = new StaffRetailShipmentSummary.ListQuery(0, 10, RetailShipmentStatus.IN_TRANSIT, null);
        var result = service.list(activeStaff(7L), query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        var summary = result.items().get(0);
        assertThat(summary.status()).isEqualTo(RetailShipmentStatus.HANDED_TO_CARRIER);
        assertThat(summary.locationId()).isEqualTo(5L);
        assertThat(summary.branchOrderId()).isNotNull();
    }

    @Test
    void detailReturnsHistoryAndGuardsOutsideLocation() {
        var repository = mock(RetailShipmentRepository.class);
        when(repository.findByIdAndLocationIdIn(SHIPMENT_ID, LOCATIONS)).thenReturn(Optional.of(newShipment(RetailShipmentStatus.PREPARING)));
        when(repository.findByIdAndLocationIdIn(eq("foreign"), any())).thenReturn(Optional.empty());
        var service = new StaffRetailShipmentService(repository);

        StaffRetailShipmentView view = service.detail(activeManager(2L), SHIPMENT_ID);
        assertThat(view.status()).isEqualTo(RetailShipmentStatus.PREPARING);
        assertThat(view.history()).isEmpty();
        assertThat(view.fulfilmentMethod()).isEqualTo("GHTK");
        assertThat(view.branchOrderId()).isNotNull();
        assertThat(view.mockTrackingNumber()).isEqualTo("S1234567890");

        assertThatThrownBy(() -> service.detail(activeStaff(7L), "foreign"))
            .isInstanceOf(RetailNotFoundException.class);
    }

    @Test
    void transitionAdvancesAndAuditsActor() {
        var repository = mock(RetailShipmentRepository.class);
        var shipment = newShipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        var service = new StaffRetailShipmentService(repository);

        StaffRetailShipmentTransitionResult result = service.transition(activeStaff(7L), SHIPMENT_ID,
            RetailShipmentStatus.HANDED_TO_CARRIER, 0, "packed");

        assertThat(result.status()).isEqualTo(RetailShipmentStatus.HANDED_TO_CARRIER);
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.HANDED_TO_CARRIER);
        assertThat(shipment.getHistory()).hasSize(1);
        assertThat(shipment.getHistory().get(0).getActorId()).isEqualTo(7L);
        assertThat(shipment.getHistory().get(0).getActorLabel()).isEqualTo("STAFF#7");
        assertThat(shipment.getHistory().get(0).getNote()).isEqualTo("packed");
        assertThat(shipment.getBranchOrder().getFulfilmentState()).isEqualTo("HANDED_TO_CARRIER");
    }

    @Test
    void transitionManagerRecordsManagerLabel() {
        var repository = mock(RetailShipmentRepository.class);
        var shipment = newShipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        var service = new StaffRetailShipmentService(repository);

        service.transition(activeManager(3L), SHIPMENT_ID, RetailShipmentStatus.HANDED_TO_CARRIER, 0, null);
        assertThat(shipment.getHistory().get(0).getActorLabel()).isEqualTo("MANAGER#3");
    }

    @Test
    void transitionRejectsSkippedAndBackwardsMoves() {
        var repository = mock(RetailShipmentRepository.class);
        var shipment = newShipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        var service = new StaffRetailShipmentService(repository);

        // skip: PREPARING -> IN_TRANSIT
        assertThatThrownBy(() -> service.transition(activeStaff(7L), SHIPMENT_ID,
            RetailShipmentStatus.IN_TRANSIT, 0, "skip"))
            .isInstanceOf(RetailConflictException.class);
        // backwards: PREPARING -> PREPARING (repeat, not allowed by entity guard)
        assertThatThrownBy(() -> service.transition(activeStaff(7L), SHIPMENT_ID,
            RetailShipmentStatus.PREPARING, 0, "repeat"))
            .isInstanceOf(RetailConflictException.class);
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.PREPARING);
        assertThat(shipment.getHistory()).isEmpty();
    }

    @Test
    void transitionRejectsStaleVersion() {
        var repository = mock(RetailShipmentRepository.class);
        var shipment = newShipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        var service = new StaffRetailShipmentService(repository);

        assertThatThrownBy(() -> service.transition(activeStaff(7L), SHIPMENT_ID,
            RetailShipmentStatus.HANDED_TO_CARRIER, 5, "stale"))
            .isInstanceOf(RetailConflictException.class);
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.PREPARING);
    }

    @Test
    void transitionRejectsOutsideLocationAndUnauthorized() {
        var repository = mock(RetailShipmentRepository.class);
        var shipment = newShipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        var service = new StaffRetailShipmentService(repository);

        var otherLocationStaff = new CurrentAccountActor(8L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(99L));
        assertThatThrownBy(() -> service.transition(otherLocationStaff, SHIPMENT_ID,
            RetailShipmentStatus.HANDED_TO_CARRIER, 0, null))
            .isInstanceOf(RetailAccessDeniedException.class);

        assertThatThrownBy(() -> service.transition(
            new CurrentAccountActor(9L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, LOCATIONS),
            SHIPMENT_ID, RetailShipmentStatus.HANDED_TO_CARRIER, 0, null))
            .isInstanceOf(RetailAccessDeniedException.class);
        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.PREPARING);
    }

    @Test
    void transitionRequiresNextStatusAndVersion() {
        var repository = mock(RetailShipmentRepository.class);
        var shipment = newShipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        var service = new StaffRetailShipmentService(repository);

        assertThatThrownBy(() -> service.transition(activeStaff(7L), SHIPMENT_ID, null, 0, null))
            .isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> service.transition(activeStaff(7L), SHIPMENT_ID,
            RetailShipmentStatus.HANDED_TO_CARRIER, null, null))
            .isInstanceOf(RetailConflictException.class);
    }

    @Test
    void transitionNotFound() {
        var repository = mock(RetailShipmentRepository.class);
        when(repository.lockById("missing")).thenReturn(Optional.empty());
        var service = new StaffRetailShipmentService(repository);

        assertThatThrownBy(() -> service.transition(activeStaff(7L), "missing",
            RetailShipmentStatus.HANDED_TO_CARRIER, 0, null))
            .isInstanceOf(RetailNotFoundException.class);
    }

    @Test
    void fullLifecycleReachesDelivered() {
        var repository = mock(RetailShipmentRepository.class);
        var shipment = newShipment(RetailShipmentStatus.PREPARING);
        when(repository.lockById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
        var service = new StaffRetailShipmentService(repository);
        var actor = activeStaff(7L);
        org.mockito.Mockito.doAnswer(invocation -> {
            org.springframework.test.util.ReflectionTestUtils.setField(shipment, "version", shipment.getVersion() + 1);
            return null;
        }).when(repository).flush();

        service.transition(actor, SHIPMENT_ID, RetailShipmentStatus.HANDED_TO_CARRIER, 0, "handed");
        service.transition(actor, SHIPMENT_ID, RetailShipmentStatus.IN_TRANSIT, 1, "transit");
        service.transition(actor, SHIPMENT_ID, RetailShipmentStatus.DELIVERED, 2, "delivered");

        assertThat(shipment.getStatus()).isEqualTo(RetailShipmentStatus.DELIVERED);
        assertThat(shipment.getBranchOrder().getFulfilmentState()).isEqualTo("DELIVERED");
        assertThat(shipment.getHistory()).hasSize(3);
        assertThat(shipment.getHistory().get(2).getStatus()).isEqualTo(RetailShipmentStatus.DELIVERED);

        // terminal: no further move allowed
        assertThatThrownBy(() -> service.transition(actor, SHIPMENT_ID,
            RetailShipmentStatus.HANDED_TO_CARRIER, 3, "after"))
            .isInstanceOf(RetailConflictException.class);
    }
}
