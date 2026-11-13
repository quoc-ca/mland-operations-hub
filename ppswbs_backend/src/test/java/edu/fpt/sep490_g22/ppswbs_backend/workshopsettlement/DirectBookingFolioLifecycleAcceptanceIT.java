package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordBookingServiceOutcomeCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementEvidenceConclusion;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BookingAttendanceFinalizedEventConsumer;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.DirectBookingFolioOpeningService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalBookingBillService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.VariableChargeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 003.3 T087 MySQL acceptance: a verified direct deposit opens exactly one OPEN folio through the
 * real Spring Modulith listener; it finalizes only at zero balance and then concludes the booking;
 * a terminal full no-show closes it; a closed folio takes no charge; and a Booking failure during a
 * conclusion never rolls back the Settlement transition.
 */
@SpringBootTest
@org.springframework.context.annotation.Import({edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyDirectBookings.class, edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyBookingLoyaltyOutcomes.class})
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class DirectBookingFolioLifecycleAcceptanceIT {

    private static final long POST_PROMOTION = 100_001L;
    private static final long DEPOSIT = 50_001L;
    private static final long BALANCE = POST_PROMOTION - DEPOSIT;

    @Autowired private edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyDirectBookings legacy;
    @Autowired private BookingInvoiceRepository invoices;
    @Autowired private BookingPaymentAttemptRepository attempts;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private SettlementFinalBillRepository bills;
    @Autowired private SettlementEvidenceConclusionRepository evidence;
    @Autowired private SettlementBusinessAuditRepository audits;
    @Autowired private DirectBookingFolioOpeningService folioOpening;
    @Autowired private FinalBookingBillService finalBills;
    @Autowired private VariableChargeService charges;
    @Autowired private BookingAttendanceFinalizedEventConsumer attendanceConsumer;

    @MockitoSpyBean private WorkshopBookingFacade bookingFacade;
    @MockitoBean private WorkshopOperationsFacade operations;

    private UUID currentSession;

    @BeforeEach
    void operationsAccept() {
        when(operations.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                true, "PUBLISHED", 1L, Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600), 10));
        when(operations.reserveBookingCapacity(any())).thenAnswer(invocation -> CapacityHoldOutcome.accepted(
                List.of(UUID.randomUUID()), Instant.now().plus(15, ChronoUnit.MINUTES)));
        when(operations.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(operations.getBookingTicketProjection(any())).thenAnswer(invocation -> new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-FOLIO-" + UUID.randomUUID(),
                        "TK-FOLIO-" + UUID.randomUUID().toString().substring(0, 12),
                        List.of(new SessionEntitlementProjection(currentSession, 1L,
                                Instant.now().plus(1, ChronoUnit.DAYS),
                                Instant.now().plus(1, ChronoUnit.DAYS).plusSeconds(3600)))))));
        when(operations.getSettlementReadiness(any())).thenReturn(new SettlementReadiness(true));
    }

    @Test
    void verifiedDepositOpensOneOpenFolioThatFinalizesOnlyAtZeroBalance() throws InterruptedException {
        String bookingCode = confirmDirectBooking();

        SettlementFinalBill folio = awaitFolio(bookingCode);
        assertThat(folio.getLifecycleStatus()).isEqualTo("OPEN");
        assertThat(folio.getPostPromotionAmountVnd()).isEqualTo(POST_PROMOTION);
        assertThat(folio.getAcceptedDepositAmountVnd()).isEqualTo(DEPOSIT);
        assertThat(folio.getRemainingAmountVnd()).isEqualTo(BALANCE);

        // Replaying the confirmation keeps the single folio.
        assertThat(folioOpening.openFolio(bookingCode, Instant.now()).orElseThrow().getBillReference())
                .isEqualTo(folio.getBillReference());
        assertThat(finalBills.create(bookingCode, "OP-RELOAD", "CORR-RELOAD").getBillReference())
                .isEqualTo(folio.getBillReference());

        assertThatThrownBy(() -> finalBills.finalizeAuthorized(bookingCode, BALANCE))
                .isInstanceOf(WorkshopSettlementConflictException.class);

        recordVerifiedFinalPayment(bookingCode, BALANCE);
        SettlementFinalBill finalized = finalBills.finalizeAuthorized(bookingCode, 0L, "OP-FIN-" + bookingCode, "CORR-FIN");

        assertThat(finalized.getLifecycleStatus()).isEqualTo("FINALIZED");
        WorkshopBooking booking = bookings.findByBookingCode(bookingCode).orElseThrow();
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        assertThatThrownBy(() -> charges.add(bookingCode, "Late add-on", 10_000L, "AFTER_FINALIZE", "OP-LATE-" + bookingCode))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void terminalFullNoShowClosesTheFolioOnceAndConcludesTheBooking() throws InterruptedException {
        String bookingCode = confirmDirectBooking();
        SettlementFinalBill folio = awaitFolio(bookingCode);
        when(operations.getBookingAttendanceOutcome(argThat(query -> query != null
                && bookingCode.equals(query.bookingReference()))))
                .thenReturn(new BookingAttendanceOutcome(true, BookingAttendanceOutcome.Outcome.NO_SHOW));

        // The consumer is an @ApplicationModuleListener (asynchronous): wait for each delivery.
        BookingAttendanceFinalizedEvent event = new BookingAttendanceFinalizedEvent(bookingCode, Instant.now());
        attendanceConsumer.onAttendanceFinalized(event);
        awaitFolioState(bookingCode, "CLOSED");
        attendanceConsumer.onAttendanceFinalized(event);
        Thread.sleep(500);

        assertThat(bills.findByBookingReference(bookingCode).orElseThrow().getLifecycleStatus()).isEqualTo("CLOSED");
        assertThat(bookings.findByBookingCode(bookingCode).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.NO_SHOW);
        assertThat(audits.findAll()).filteredOn(audit -> folio.getBillReference().equals(audit.getAggregateReference())
                && "DIRECT_FOLIO_CLOSED_NO_SHOW".equals(audit.getActionCode())).hasSize(1);
        assertThatThrownBy(() -> charges.add(bookingCode, "Add-on", 10_000L, "AFTER_CLOSE", "OP-CLOSED-" + bookingCode))
                .isInstanceOf(WorkshopSettlementConflictException.class);
    }

    @Test
    void partialAttendanceKeepsTheFolioOpenForCheckout() throws InterruptedException {
        String bookingCode = confirmDirectBooking();
        awaitFolio(bookingCode);
        when(operations.getBookingAttendanceOutcome(argThat(query -> query != null
                && bookingCode.equals(query.bookingReference()))))
                .thenReturn(new BookingAttendanceOutcome(true, BookingAttendanceOutcome.Outcome.PARTIAL));

        attendanceConsumer.onAttendanceFinalized(new BookingAttendanceFinalizedEvent(bookingCode, Instant.now()));
        Thread.sleep(1000);

        assertThat(bills.findByBookingReference(bookingCode).orElseThrow().getLifecycleStatus()).isEqualTo("OPEN");
        assertThat(bookings.findByBookingCode(bookingCode).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void aBookingFailureDuringTheConclusionDoesNotRollBackFinalization() throws InterruptedException {
        String bookingCode = confirmDirectBooking();
        awaitFolio(bookingCode);
        recordVerifiedFinalPayment(bookingCode, BALANCE);
        doThrow(new IllegalStateException("simulated Booking failure")).when(bookingFacade)
                .recordBookingServiceOutcome(argThat((RecordBookingServiceOutcomeCommand command) -> command != null
                        && bookingCode.equals(command.bookingReference())));

        SettlementFinalBill finalized = finalBills.finalizeAuthorized(bookingCode, 0L, "OP-FIN-" + bookingCode, "CORR-FIN");

        assertThat(finalized.getLifecycleStatus()).isEqualTo("FINALIZED");
        assertThat(bills.findByBookingReference(bookingCode).orElseThrow().getLifecycleStatus()).isEqualTo("FINALIZED");
        // The balance conclusion committed in its own transaction; the failed service outcome left CONFIRMED.
        WorkshopBooking booking = bookings.findByBookingCode(bookingCode).orElseThrow();
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.FULLY_PAID);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    private String confirmDirectBooking() {
        String bookingCode = "BK-FOLIO-" + UUID.randomUUID().toString().substring(0, 8);
        currentSession = UUID.randomUUID();
        legacy.pending(bookingCode, 1L, "Test package", "buyer@example.test", "0900000000", null,
                1, List.of(currentSession), 1L, POST_PROMOTION, POST_PROMOTION);
        String merchant = legacy.depositAttempt(bookingCode, null);
        String transactionNumber = String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        assertThat(legacy.confirmDeposit(bookingCode, merchant, transactionNumber)).isTrue();
        return bookingCode;
    }

    /** The confirmation event reaches the folio listener asynchronously after commit. */
    private SettlementFinalBill awaitFolio(String bookingCode) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Optional<SettlementFinalBill> folio = bills.findByBookingReference(bookingCode);
            if (folio.isPresent()) return folio.get();
            Thread.sleep(100);
        }
        throw new AssertionError("No folio opened for " + bookingCode);
    }

    private void awaitFolioState(String bookingCode, String state) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (bills.findByBookingReference(bookingCode).map(SettlementFinalBill::getLifecycleStatus)
                    .filter(state::equals).isPresent()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Folio for " + bookingCode + " never reached " + state);
    }

    private void recordVerifiedFinalPayment(String bookingCode, long amount) {
        String reference = "FINAL-" + UUID.randomUUID();
        evidence.saveAndFlush(SettlementEvidenceConclusion.builder()
                .evidenceReference(reference).bookingReference(bookingCode)
                .conclusion("FINAL_PAYMENT_VERIFIED").amountVnd(amount).currencyCode("VND")
                .operationReference("OP-" + reference).createdAt(Instant.now()).build());
    }
}
