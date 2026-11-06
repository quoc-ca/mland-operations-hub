package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandBookingConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingDepositPaymentsProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.VerifiedBookingPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBillLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.DirectBookingFolioOpeningService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.MlandBookingConfirmedFolioConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectBookingFolioOpeningTest {

    @Mock
    private SettlementFinalBillRepository bills;
    @Mock
    private WorkshopBookingSettlementAdapter booking;

    private DirectBookingFolioOpeningService openingService;
    private MlandBookingConfirmedFolioConsumer consumer;

    @BeforeEach
    void setUp() {
        openingService = new DirectBookingFolioOpeningService(bills, booking);
        consumer = new MlandBookingConfirmedFolioConsumer(openingService);
    }

    @Test
    void opensSingleFolioWithFrozenAmountsAndVerifiedDeposit() {
        String bookingRef = "BK-DIRECT-101";
        Instant confirmedAt = Instant.parse("2026-11-05T10:00:00Z");
        when(bills.findByBookingReference(bookingRef)).thenReturn(Optional.empty());
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-10", 200_000L, 180_000L, 90_000L, 90_000L, 2, List.of("SES-1"), 5L));
        when(booking.verifiedDepositPayments(bookingRef)).thenReturn(new VerifiedBookingDepositPaymentsProjection(List.of(
                new VerifiedBookingPayment("DEP-TXN-1", "VNPAY-1001", "20261105100000", 90_000L, "VND", true))));
        when(bills.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<SettlementFinalBill> optResult = openingService.openFolio(bookingRef, confirmedAt);

        assertThat(optResult).isPresent();
        SettlementFinalBill created = optResult.get();
        assertThat(created.getBookingReference()).isEqualTo(bookingRef);
        assertThat(created.getPostPromotionAmountVnd()).isEqualTo(180_000L);
        assertThat(created.getAcceptedDepositAmountVnd()).isEqualTo(90_000L);
        assertThat(created.getRemainingAmountVnd()).isEqualTo(90_000L);
        assertThat(created.getLifecycleStatus()).isEqualTo(SettlementFinalBillLifecycleStatus.OPEN.name());
        assertThat(created.getCurrencyCode()).isEqualTo("VND");
        assertThat(created.getFrozenAt()).isEqualTo(confirmedAt);

        verify(bills).saveAndFlush(any(SettlementFinalBill.class));
    }

    @Test
    void replayIsIdempotentAndReturnsExistingFolio() {
        String bookingRef = "BK-DIRECT-102";
        SettlementFinalBill existing = SettlementFinalBill.builder()
                .billReference("BILL-EX-1")
                .bookingReference(bookingRef)
                .postPromotionAmountVnd(200_000L)
                .acceptedDepositAmountVnd(100_000L)
                .remainingAmountVnd(100_000L)
                .currencyCode("VND")
                .lifecycleStatus(SettlementFinalBillLifecycleStatus.OPEN.name())
                .build();
        when(bills.findByBookingReference(bookingRef)).thenReturn(Optional.of(existing));

        Optional<SettlementFinalBill> optResult = openingService.openFolio(bookingRef, Instant.now());

        assertThat(optResult).containsSame(existing);
        verifyNoInteractions(booking);
        verify(bills, never()).saveAndFlush(any());
    }

    @Test
    void skipsFolioCreationWhenProjectionUnavailable() {
        String bookingRef = "BK-NO-PROJECTION";
        when(bills.findByBookingReference(bookingRef)).thenReturn(Optional.empty());
        when(booking.confirmedProjection(bookingRef)).thenReturn(null);

        Optional<SettlementFinalBill> optResult = openingService.openFolio(bookingRef, Instant.now());

        assertThat(optResult).isEmpty();
        verify(bills, never()).saveAndFlush(any());
    }

    @Test
    void consumerDelegatesToOpeningService() {
        String bookingRef = "BK-EVENT-1";
        Instant confirmedAt = Instant.parse("2026-11-05T12:00:00Z");
        when(bills.findByBookingReference(bookingRef)).thenReturn(Optional.empty());
        when(booking.confirmedProjection(bookingRef)).thenReturn(new ConfirmedBookingFinancialProjection(
                "PKG-1", 100_000L, 100_000L, 50_000L, 50_000L, 1, List.of("SES-1"), 1L));
        when(booking.verifiedDepositPayments(bookingRef)).thenReturn(new VerifiedBookingDepositPaymentsProjection(List.of(
                new VerifiedBookingPayment("DEP-1", "VNPAY-1", "20261105120000", 50_000L, "VND", true))));
        when(bills.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onBookingConfirmed(new MlandBookingConfirmedEvent(bookingRef, confirmedAt));

        verify(bills).saveAndFlush(argThat(bill -> bookingRef.equals(bill.getBookingReference())
                && confirmedAt.equals(bill.getFrozenAt())));
    }
}
