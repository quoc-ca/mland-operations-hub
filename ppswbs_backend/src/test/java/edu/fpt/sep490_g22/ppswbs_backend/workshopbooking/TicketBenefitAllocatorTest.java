package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentMode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketCollectionKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.TicketBenefitAllocator;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.TicketBenefitAllocator.GroupResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.TicketBenefitAllocator.TicketInput;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.TicketBenefitAllocator.TicketResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 003.1 FR-033..FR-036: per-ticket allocation (largest remainder) and booking-time collection. */
class TicketBenefitAllocatorTest {

    private static TicketInput t(long price) {
        return new TicketInput(price, true);
    }

    private static GroupResult run(List<TicketInput> tickets, long voucher, long points, long pointValue) {
        return TicketBenefitAllocator.allocate(tickets, voucher, points, pointValue);
    }

    @Test
    void voucherIsSplitByPriceWithNoRemainder() {
        GroupResult r = run(List.of(t(300_000), t(300_000), t(400_000)), 100_000, 0, 100);

        assertThat(r.tickets()).extracting(TicketResult::voucherDiscountVnd).containsExactly(30_000L, 30_000L, 40_000L);
    }

    @Test
    void equalTicketsGiveTheLeftoverDongToTheFirstInCartOrder() {
        GroupResult r = run(List.of(t(300_000), t(300_000), t(300_000)), 100_000, 0, 100);

        assertThat(r.tickets()).extracting(TicketResult::voucherDiscountVnd)
                .containsExactly(33_334L, 33_333L, 33_333L);
    }

    @Test
    void largestRemainderThenHigherPriceBreaksTies() {
        // 10 over weights 3 and 7: floors 3 and 7 = 10, no leftover.
        assertThat(TicketBenefitAllocator.spread(10, new long[]{3, 7}, new long[]{3, 7})).containsExactly(3, 7);
        // 5 over weights 1,1,2: floors 1,1,2 = 4, leftover 1 goes to the ticket with the largest remainder.
        long[] shares = TicketBenefitAllocator.spread(5, new long[]{1, 1, 2}, new long[]{5, 5, 5});
        assertThat(TicketBenefitAllocator.sum(shares)).isEqualTo(5);
        assertThat(shares).containsExactly(1, 1, 3);
        // 1 over weights 2 and 3: remainders 2 and 3, the larger remainder wins.
        assertThat(TicketBenefitAllocator.spread(1, new long[]{2, 3}, new long[]{9, 9})).containsExactly(0, 1);
        // Equal remainders (2 over weights 1 and 3 → floors 0,1, remainders 2,2): the heavier, later ticket wins.
        assertThat(TicketBenefitAllocator.spread(2, new long[]{1, 3}, new long[]{9, 9})).containsExactly(0, 2);
        // Equal remainder and equal weight: earlier cart position wins.
        assertThat(TicketBenefitAllocator.spread(1, new long[]{2, 2}, new long[]{9, 9})).containsExactly(1, 0);
    }

    @Test
    void totalAlwaysEqualsTheGroupDiscount() {
        for (long discount : new long[]{1, 7, 99_999, 1_000_001}) {
            GroupResult r = run(List.of(t(333_333), t(250_001), t(410_007), t(99_999)), discount, 0, 100);
            assertThat(r.tickets().stream().mapToLong(TicketResult::voucherDiscountVnd).sum()).isEqualTo(discount);
        }
    }

    @Test
    void voucherNeverExceedsATicketPriceAndSkipsOutOfScopeTickets() {
        // Ticket B is out of scope; the whole 150,000 discount must land on A and C, capped at their prices.
        GroupResult r = run(List.of(t(100_000), new TicketInput(500_000, false), t(100_000)), 150_000, 0, 100);

        assertThat(r.tickets().get(1).voucherDiscountVnd()).isZero();
        assertThat(r.tickets().get(0).voucherDiscountVnd()).isEqualTo(75_000);
        assertThat(r.tickets().get(2).voucherDiscountVnd()).isEqualTo(75_000);
        assertThatThrownBy(() -> run(List.of(t(100_000), new TicketInput(500_000, false)), 150_000, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hundredPercentVoucherMakesEveryTicketFreeAndTheGroupInternal() {
        GroupResult r = run(List.of(t(300_000), t(250_000)), 550_000, 0, 100);

        assertThat(r.tickets()).allSatisfy(x -> {
            assertThat(x.finalPriceVnd()).isZero();
            assertThat(x.collectionKind()).isEqualTo(TicketCollectionKind.FREE);
        });
        assertThat(r.paymentMode()).isEqualTo(GroupPaymentMode.INTERNAL_FREE);
        assertThat(r.groupCollectionVnd()).isZero();
    }

    @Test
    void pointsFollowPostVoucherPriceAndAZeroTicketGetsNone() {
        // B is outside the voucher scope, so A is free after the voucher and all 100 points (10,000 VND) go to B.
        GroupResult r = run(List.of(t(200_000), new TicketInput(300_000, false)), 200_000, 100, 100);

        assertThat(r.tickets().get(0).pointsUsed()).isZero();
        assertThat(r.tickets().get(1).pointsUsed()).isEqualTo(100);
        assertThat(r.tickets().get(1).pointDiscountVnd()).isEqualTo(10_000);
        assertThat(r.tickets().get(1).finalPriceVnd()).isEqualTo(290_000);
    }

    @Test
    void pointsAreWholeAndSplitByLargestRemainder() {
        // 3 points over two equal tickets: 2 and 1.
        GroupResult r = run(List.of(t(100_000), t(100_000)), 0, 3, 100);

        assertThat(r.tickets()).extracting(TicketResult::pointsUsed).containsExactly(2L, 1L);
        assertThat(r.tickets()).extracting(TicketResult::pointDiscountVnd).containsExactly(200L, 100L);
    }

    @Test
    void pointsCanBringTheWholeCartToZero() {
        // Cap 100%: 1,000 points at 100 VND pay a 100,000 ticket completely.
        GroupResult r = run(List.of(t(60_000), t(40_000)), 0, 1_000, 100);

        assertThat(r.tickets()).extracting(TicketResult::finalPriceVnd).containsExactly(0L, 0L);
        assertThat(r.paymentMode()).isEqualTo(GroupPaymentMode.INTERNAL_FREE);
    }

    @Test
    void aTicketNeverSpendsMorePointsThanItCosts() {
        // Ticket A costs 150 VND = 1 whole point of 100; the rest of the 3 points must move to B.
        GroupResult r = run(List.of(t(150), t(1_000)), 0, 3, 100);

        assertThat(r.tickets().get(0).pointsUsed()).isLessThanOrEqualTo(1);
        assertThat(r.tickets().stream().mapToLong(TicketResult::pointsUsed).sum()).isEqualTo(3);
        assertThat(r.tickets()).allSatisfy(x -> assertThat(x.finalPriceVnd()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void pointsThatCannotBePlacedAreRejected() {
        assertThatThrownBy(() -> run(List.of(t(1_000)), 0, 11, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "price {0} → {2} {1}")
    @CsvSource({
            "0,FREE,0,0",
            "1,FULL,1,0",
            "4999,FULL,4999,0",
            "5000,FULL,5000,0",
            "200000,FULL,200000,0",
            "200001,FULL,200001,0",
            "204999,FULL,204999,0",
            "205000,DEPOSIT,200000,5000",
            "500000,DEPOSIT,200000,300000"
    })
    void singleTicketCollectionBands(long price, TicketCollectionKind kind, long collected, long remaining) {
        // Group minimum is checked separately; pair with a 500,000 ticket so the group always reaches 5,000.
        GroupResult r = run(List.of(t(price), t(500_000)), 0, 0, 100);

        TicketResult ticket = r.tickets().get(0);
        assertThat(ticket.collectionKind()).isEqualTo(kind);
        assertThat(ticket.collectedAtBookingVnd()).isEqualTo(collected);
        assertThat(ticket.remainingVnd()).isEqualTo(remaining);
        assertThat(ticket.collectedAtBookingVnd() + ticket.remainingVnd()).isEqualTo(ticket.finalPriceVnd());
    }

    @Test
    void mixedCartCollectsPerTicketAndAddsUpToOneVnpayPayment() {
        // A free, B 203,000 full, C 500,000 deposit: 403,000 in one payment, 300,000 left for C.
        GroupResult r = run(List.of(t(0), t(203_000), t(500_000)), 0, 0, 100);

        assertThat(r.paymentMode()).isEqualTo(GroupPaymentMode.VNPAY);
        assertThat(r.groupCollectionVnd()).isEqualTo(403_000);
        assertThat(r.tickets()).extracting(TicketResult::remainingVnd).containsExactly(0L, 0L, 300_000L);
    }

    @Test
    void aSmallTicketDoesNotBlockTheGroupWhenTheTotalReachesTheMinimum() {
        // 3,000 + 500,000: the 5,000 floor applies to the group (203,000), not to the 3,000 ticket.
        GroupResult r = run(List.of(t(3_000), t(500_000)), 0, 0, 100);

        assertThat(r.paymentMode()).isEqualTo(GroupPaymentMode.VNPAY);
        assertThat(r.groupCollectionVnd()).isEqualTo(203_000);
        assertThat(r.tickets().get(0).collectionKind()).isEqualTo(TicketCollectionKind.FULL);
    }

    @Test
    void groupUnderTheMinimumIsDeferredToCheckout() {
        GroupResult r = run(List.of(t(3_000), t(0)), 0, 0, 100);

        assertThat(r.paymentMode()).isEqualTo(GroupPaymentMode.INTERNAL_DEFERRED);
        assertThat(r.groupCollectionVnd()).isZero();
        TicketResult paying = r.tickets().get(0);
        assertThat(paying.collectionKind()).isEqualTo(TicketCollectionKind.DEFERRED_BELOW_VNPAY_MINIMUM);
        assertThat(paying.collectedAtBookingVnd()).isZero();
        assertThat(paying.remainingVnd()).isEqualTo(3_000);
        assertThat(r.tickets().get(1).collectionKind()).isEqualTo(TicketCollectionKind.FREE);
    }

    @ParameterizedTest(name = "group collection {0} → {1}")
    @CsvSource({"1,INTERNAL_DEFERRED", "4999,INTERNAL_DEFERRED", "5000,VNPAY", "5001,VNPAY"})
    void groupMinimumBoundaries(long price, GroupPaymentMode mode) {
        assertThat(run(List.of(t(price)), 0, 0, 100).paymentMode()).isEqualTo(mode);
    }

    @Test
    void pointsMayLeaveAGroupBelowTheMinimumAndItIsDeferred() {
        // 20,000 ticket, 15,500 VND of points (155 points) leaves 4,500: deferred, not refused.
        GroupResult r = run(List.of(t(20_000)), 0, 155, 100);

        assertThat(r.tickets().get(0).finalPriceVnd()).isEqualTo(4_500);
        assertThat(r.paymentMode()).isEqualTo(GroupPaymentMode.INTERNAL_DEFERRED);
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> run(List.of(), 0, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(List.of(t(1_000)), -1, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run(List.of(t(1_000)), 0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TicketInput(-1, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
