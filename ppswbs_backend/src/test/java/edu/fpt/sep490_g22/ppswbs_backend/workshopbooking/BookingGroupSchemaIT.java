package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentMode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketCollectionKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketPriceSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V111 on MySQL: the group/ticket schema stores the 2026-11-12 collection rules and the database itself refuses
 * invalid rows. Each write runs in its own transaction so a refused insert cannot poison the next step; created
 * groups are deleted afterwards (tickets, snapshots and the other group tables cascade).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class BookingGroupSchemaIT {

    @Autowired private BookingGroupRepository groups;
    @Autowired private TicketBookingRepository tickets;
    @Autowired private TicketPriceSnapshotRepository snapshots;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> createdGroups = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdGroups.forEach(groups::deleteById);
        createdGroups.clear();
    }

    @Test
    void storesAGroupWithTicketsAndImmutablePriceSnapshots() {
        BookingGroup group = newGroup(GroupPaymentMode.VNPAY, 203_000);
        TicketBooking free = newTicket(group, 0, 1);
        TicketBooking full = newTicket(group, 0, 2);
        TicketBooking deposit = newTicket(group, 1, 1);
        save(snapshot(free, 300_000, 300_000, 0, 0, TicketCollectionKind.FREE, 0));
        save(snapshot(full, 203_000, 0, 0, 203_000, TicketCollectionKind.FULL, 0));
        save(snapshot(deposit, 500_000, 0, 0, 200_000, TicketCollectionKind.DEPOSIT, 300_000));

        assertThat(groups.findByGroupCode(group.getGroupCode())).isPresent();
        List<TicketBooking> stored = tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId());
        assertThat(stored).hasSize(3).extracting(TicketBooking::getTicketStatus)
                .containsOnly(BookingStatus.PENDING_PAYMENT);
        assertThat(stored).extracting(TicketBooking::getFinancialStatus).containsOnly(PaymentStatus.UNPAID);
        assertThat(snapshots.findByTicketId(deposit.getId()).orElseThrow().getRemainingVnd()).isEqualTo(300_000);
    }

    @Test
    void groupCollectionRulesAreEnforcedByTheDatabase() {
        // VNPay needs at least 5,000 VND; internal confirmation collects nothing.
        refusedBy("ck_bg_vnpay_floor", () -> groups.saveAndFlush(group(GroupPaymentMode.VNPAY, 4_999)));
        refusedBy("ck_bg_internal_zero", () -> groups.saveAndFlush(group(GroupPaymentMode.INTERNAL_DEFERRED, 3_000)));
        assertThat(newGroup(GroupPaymentMode.VNPAY, 5_000).getId()).isNotNull();
        assertThat(newGroup(GroupPaymentMode.INTERNAL_DEFERRED, 0).getId()).isNotNull();
    }

    @Test
    void collectionKindMustMatchThePriceBands() {
        BookingGroup group = newGroup(GroupPaymentMode.VNPAY, 400_000);
        TicketBooking one = newTicket(group, 0, 1);
        TicketBooking two = newTicket(group, 0, 2);
        TicketBooking three = newTicket(group, 0, 3);
        // A deposit is exactly 200,000 above 200,000; up to 200,000 is paid in full.
        refusedBy("ck_tps_kind", () -> snapshots.saveAndFlush(
                snapshot(one, 500_000, 0, 0, 150_000, TicketCollectionKind.DEPOSIT, 350_000)));
        refusedBy("ck_tps_kind", () -> snapshots.saveAndFlush(
                snapshot(two, 200_000, 0, 0, 150_000, TicketCollectionKind.DEPOSIT, 50_000)));
        refusedBy("ck_tps_kind", () -> snapshots.saveAndFlush(
                snapshot(three, 200_001, 0, 0, 200_001, TicketCollectionKind.FULL, 0)));
        // The boundaries themselves are accepted.
        save(snapshot(one, 200_000, 0, 0, 200_000, TicketCollectionKind.FULL, 0));
        save(snapshot(two, 200_001, 0, 0, 200_000, TicketCollectionKind.DEPOSIT, 1));
    }

    @Test
    void snapshotAmountsMustAddUp() {
        BookingGroup group = newGroup(GroupPaymentMode.VNPAY, 100_000);
        TicketBooking one = newTicket(group, 0, 1);
        // collected + remaining must equal the final price.
        refusedBy("ck_tps_amounts", () -> snapshots.saveAndFlush(
                snapshot(one, 100_000, 0, 0, 90_000, TicketCollectionKind.FULL, 20_000)));
    }

    @Test
    void aTicketHasOnePersonSlotAndOneSnapshot() {
        BookingGroup group = newGroup(GroupPaymentMode.VNPAY, 100_000);
        TicketBooking first = newTicket(group, 0, 1);
        refusedBy("uq_tb_group_line_person", () -> tickets.saveAndFlush(ticket(group, 0, 1)));
        TicketBooking second = newTicket(group, 0, 2);
        save(snapshot(second, 100_000, 0, 0, 100_000, TicketCollectionKind.FULL, 0));
        assertThat(first.getId()).isNotEqualTo(second.getId());
        refusedBy("uq_tps_ticket", () -> snapshots.saveAndFlush(
                snapshot(second, 100_000, 0, 0, 100_000, TicketCollectionKind.FULL, 0)));
    }

    private BookingGroup newGroup(GroupPaymentMode mode, long collection) {
        BookingGroup saved = committed(() -> groups.saveAndFlush(group(mode, collection)));
        createdGroups.add(saved.getId());
        return saved;
    }

    private TicketBooking newTicket(BookingGroup group, int line, int person) {
        return committed(() -> tickets.saveAndFlush(ticket(group, line, person)));
    }

    private void save(TicketPriceSnapshot snapshot) {
        committed(() -> snapshots.saveAndFlush(snapshot));
    }

    /** Runs the write in its own transaction and expects MySQL to refuse it through the named constraint. */
    private void refusedBy(String constraint, Runnable write) {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> write.run()))
                .hasMessageContaining(constraint);
    }

    private <T> T committed(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private BookingGroup group(GroupPaymentMode mode, long collection) {
        return BookingGroup.builder().groupCode("GRP-" + UUID.randomUUID().toString().substring(0, 12))
                .contactEmail("buyer@example.test").contactPhone("0900000000")
                .groupStatus(BookingStatus.PENDING_PAYMENT).paymentMode(mode).groupCollectionVnd(collection).build();
    }

    private TicketBooking ticket(BookingGroup group, int line, int person) {
        return TicketBooking.builder()
                .ticketCode("TKB-" + UUID.randomUUID().toString().substring(0, 12)).group(group)
                .lineIndex(line).participantOrdinal(person).packageId(15L).packageNameSnapshot("Silverclay").build();
    }

    private TicketPriceSnapshot snapshot(TicketBooking ticket, long price, long voucher, long pointDiscount,
                                         long collected, TicketCollectionKind kind, long remaining) {
        return TicketPriceSnapshot.builder().ticket(ticket).packagePriceVnd(price).voucherDiscountVnd(voucher)
                .pointsUsed(pointDiscount / 100).pointDiscountVnd(pointDiscount)
                .finalPriceVnd(price - voucher - pointDiscount)
                .collectedAtBookingVnd(collected).collectionKind(kind).remainingVnd(remaining)
                .nonRefundableDisclosed(true).build();
    }
}
