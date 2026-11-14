package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.CatalogueSalesException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailBranchAvailabilityGuardRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantAvailabilityGuardRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailBranchAvailabilityGuard;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantAvailabilityGuard;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailVariantHoldStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailAvailabilityGuardService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailVariantHoldLifecycleService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetailAvailabilityGuardServiceTest {

    private final RetailVariantAvailabilityGuardRepository guards = mock(RetailVariantAvailabilityGuardRepository.class);
    private final RetailVariantHoldRepository holds = mock(RetailVariantHoldRepository.class);
    private final RetailBranchAvailabilityGuardRepository branchGuards = mock(RetailBranchAvailabilityGuardRepository.class);
    private final RetailAvailabilityGuardService service = new RetailAvailabilityGuardService(guards, holds, branchGuards);
    private final RetailVariantHoldLifecycleService lifecycle = new RetailVariantHoldLifecycleService(holds, service);
    private final Instant now = Instant.parse("2026-10-29T10:00:00Z");

    @Test
    void tracksHoldsPerBranch() {
        assertThat(service.tracksHoldsPerBranch()).isTrue();
    }

    @Test
    void branchSnapshotsCountOnlyThatBranchPlusLegacySizeHolds() {
        when(holds.sumValidHeldByVariantAndLocation(eq(List.of(101L)), eq(RetailVariantHoldStatus.HELD), any())).thenReturn(List.of(
            branchHeld(101L, 10L, 2L), branchHeld(101L, RetailVariantHold.UNATTRIBUTED_LOCATION, 1L)));
        when(branchGuards.findByVariantIdIn(List.of(101L))).thenReturn(List.of());

        var snapshots = service.readBranchSnapshots(List.of(new BranchVariant(101L, 20L), new BranchVariant(101L, 10L)), now);

        assertThat(snapshots.get(new BranchVariant(101L, 10L)).heldQuantity()).isEqualTo(java.math.BigInteger.valueOf(3));
        assertThat(snapshots.get(new BranchVariant(101L, 20L)).heldQuantity()).isEqualTo(java.math.BigInteger.ONE);
    }

    @Test
    void createsABranchHoldUnderTheBranchGuardAfterLockingTheSizeGuard() {
        RetailVariantAvailabilityGuard sizeGuard = guard(2L, 0L);
        RetailBranchAvailabilityGuard branchGuard = mock(RetailBranchAvailabilityGuard.class);
        when(branchGuard.getVersion()).thenReturn(3L);
        GroupedCheckout checkout = mock(GroupedCheckout.class);
        when(checkout.getId()).thenReturn("checkout-1");
        when(guards.findByVariantIdInOrderByVariantIdAsc(List.of(2L))).thenReturn(List.of(sizeGuard));
        when(branchGuards.lockOne(2L, 10L)).thenReturn(java.util.Optional.of(branchGuard));
        when(holds.sumValidHeldByVariantIds(eq(List.of(2L)), eq(RetailVariantHoldStatus.HELD), any())).thenReturn(List.of());
        when(holds.sumValidHeldByVariantAndLocation(eq(List.of(2L)), eq(RetailVariantHoldStatus.HELD), any())).thenReturn(List.of());
        when(holds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RetailVariantHold created = service.withBranchGuard(List.of(new BranchVariant(2L, 10L)), now, snapshots -> {
            assertThat(snapshots.get(new BranchVariant(2L, 10L)).sourceRevision()).isEqualTo(3L);
            return lifecycle.createHeld(checkout, 2L, 10L, 1, now.plusSeconds(900));
        });

        assertThat(created.getLocationId()).isEqualTo(10L);
        var order = inOrder(guards, branchGuards);
        order.verify(guards).findByVariantIdInOrderByVariantIdAsc(List.of(2L));
        order.verify(branchGuards).lockOne(2L, 10L);
        verify(branchGuard).touch();
        verify(sizeGuard).touch();
    }

    @Test
    void rejectsABranchHoldOutsideItsGuard() {
        GroupedCheckout checkout = mock(GroupedCheckout.class);
        when(checkout.getId()).thenReturn("checkout-1");

        assertThatThrownBy(() -> lifecycle.createHeld(checkout, 2L, 10L, 1, now.plusSeconds(900)))
            .isInstanceOf(CatalogueSalesException.class);
    }

    private static RetailVariantHoldRepository.BranchHeldQuantity branchHeld(long variantId, long locationId, long quantity) {
        return new RetailVariantHoldRepository.BranchHeldQuantity() {
            @Override public Long getVariantId() { return variantId; }
            @Override public Long getLocationId() { return locationId; }
            @Override public Long getHeldQuantity() { return quantity; }
        };
    }

    @Test
    void returnsVerifiedZeroForAVariantWithoutAGuardOrHold() {
        when(guards.findAllById(List.of(101L))).thenReturn(List.of());
        when(holds.sumValidHeldByVariantIds(List.of(101L), RetailVariantHoldStatus.HELD, now)).thenReturn(List.of());

        Map<Long, ?> snapshots = service.readSnapshots(List.of(101L), now);

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(101L).toString()).contains("heldQuantity=0", "sourceRevision=0");
    }

    @Test
    void provisionsAndLocksGuardsInAscendingVariantOrder() {
        RetailVariantAvailabilityGuard two = guard(2L, 4L);
        RetailVariantAvailabilityGuard nine = guard(9L, 7L);
        when(guards.findByVariantIdInOrderByVariantIdAsc(List.of(2L, 9L))).thenReturn(List.of(two, nine));
        when(holds.sumValidHeldByVariantIds(eq(List.of(2L, 9L)), eq(RetailVariantHoldStatus.HELD), any())).thenReturn(List.of());

        Map<Long, ?> snapshots = service.withGuard(List.of(9L, 2L, 9L), now, values -> values);

        assertThat(snapshots.keySet()).containsExactlyInAnyOrder(2L, 9L);
        var order = inOrder(guards);
        order.verify(guards).provisionIfAbsent(2L);
        order.verify(guards).provisionIfAbsent(9L);
        order.verify(guards).flush();
        order.verify(guards).findByVariantIdInOrderByVariantIdAsc(List.of(2L, 9L));
    }

    @Test
    void rejectsAnIncompleteGuardLock() {
        when(guards.findByVariantIdInOrderByVariantIdAsc(List.of(2L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.withGuard(List.of(2L), now, values -> null))
            .isInstanceOf(CatalogueSalesException.class)
            .extracting(error -> ((CatalogueSalesException) error).reason())
            .isEqualTo(CatalogueSalesException.Reason.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void createsAHoldOnlyInsideTheActiveVariantGuard() {
        RetailVariantAvailabilityGuard guard = guard(2L, 0L);
        GroupedCheckout checkout = mock(GroupedCheckout.class);
        when(checkout.getId()).thenReturn("checkout-1");
        when(guards.findByVariantIdInOrderByVariantIdAsc(List.of(2L))).thenReturn(List.of(guard));
        when(holds.sumValidHeldByVariantIds(eq(List.of(2L)), eq(RetailVariantHoldStatus.HELD), any())).thenReturn(List.of());
        when(holds.findByGroupedCheckoutIdAndVariantId("checkout-1", 2L)).thenReturn(java.util.Optional.empty());
        when(holds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.withGuard(List.of(2L), now, ignored -> lifecycle.createHeld(checkout, 2L, 1, now.plusSeconds(900)));

        verify(guard).touch();
        verify(holds).save(any());
    }

    @Test
    void releasesAHoldAndBumpsItsGuardOnlyInsideTheActiveVariantGuard() {
        RetailVariantAvailabilityGuard guard = guard(2L, 0L);
        RetailVariantHold hold = new RetailVariantHold(mock(GroupedCheckout.class), 2L, 1, now.plusSeconds(900));
        when(guards.findByVariantIdInOrderByVariantIdAsc(List.of(2L))).thenReturn(List.of(guard));
        when(holds.sumValidHeldByVariantIds(eq(List.of(2L)), eq(RetailVariantHoldStatus.HELD), any())).thenReturn(List.of());

        service.withGuard(List.of(2L), now, ignored -> {
            lifecycle.release(hold);
            return null;
        });

        assertThat(hold.getStatus()).isEqualTo(RetailVariantHoldStatus.RELEASED);
        verify(guard).touch();
    }

    @Test
    void rejectsHoldStateChangesOutsideAnActiveVariantGuard() {
        RetailVariantHold hold = new RetailVariantHold(mock(GroupedCheckout.class), 2L, 1, now.plusSeconds(900));

        assertThatThrownBy(() -> lifecycle.expire(hold))
            .isInstanceOf(CatalogueSalesException.class)
            .extracting(error -> ((CatalogueSalesException) error).reason())
            .isEqualTo(CatalogueSalesException.Reason.DEPENDENCY_UNAVAILABLE);

        assertThat(hold.getStatus()).isEqualTo(RetailVariantHoldStatus.HELD);
    }

    private RetailVariantAvailabilityGuard guard(long variantId, long version) {
        RetailVariantAvailabilityGuard guard = mock(RetailVariantAvailabilityGuard.class);
        when(guard.getVariantId()).thenReturn(variantId);
        when(guard.getVersion()).thenReturn(version);
        return guard;
    }
}
