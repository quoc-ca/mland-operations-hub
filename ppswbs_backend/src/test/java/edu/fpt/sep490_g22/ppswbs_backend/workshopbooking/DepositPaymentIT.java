package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.dao.PaymentIngressReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.PaymentIngressService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "vnpay.hash-secret=deposit-it-secret")
class DepositPaymentIT {
    @Autowired private PaymentIngressService ingress;
    @Autowired private VnpayChecksumService checksum;
    @Autowired private PaymentOwnerRegistry owners;
    @Autowired private PaymentIngressReceiptRepository ingressReceipts;
    @Autowired private BookingLifecycleService lifecycle;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private BookingInvoiceRepository invoices;
    @Autowired private BookingPaymentAttemptRepository attempts;
    @Autowired private BookingPaymentReceiptRepository paymentReceipts;
    @Autowired private MockMvc mvc;
    @MockBean private WorkshopOperationsFacade operations;

    @Test
    void signedSuccessConfirmsBookingAndDuplicateDeliveryDoesNotCreateSecondReceipt() {
        Fixture fixture = pendingBooking();
        configureTicketProjection(fixture.sessionReference());
        Map<String, String> callback = signedCallback(fixture.merchantReference(), "5000000", "00", "00", "TX-OK");

        var first = ingress.receive(callback);
        var duplicate = ingress.receive(callback);

        assertThat(first.message()).isEqualTo("OK");
        assertThat(duplicate.message()).isEqualTo("Already received");
        assertThat(bookings.findByBookingCode(fixture.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentReceipts.findByBookingId(fixture.bookingId())).hasSize(1);
        assertThat(ingressReceipts.existsByDeliveryReference(
                fixture.merchantReference() + "|TX-OK|00|00")).isTrue();
        verify(operations, times(1)).commitBookingHolds(any());
        verify(operations, times(1)).getBookingTicketProjection(any());
    }

    @Test
    void invalidSignatureAndSignedAmountMismatchLeaveBookingUnpaidAndPersistNoIngressReceipt() {
        Fixture fixture = pendingBooking();
        Map<String, String> invalid = signedCallback(fixture.merchantReference(), "5000000", "00", "00", "TX-BAD-HASH");
        invalid.put("vnp_SecureHash", "invalid");
        var invalidResult = ingress.receive(invalid);

        Map<String, String> mismatch = signedCallback(fixture.merchantReference(), "5100000", "00", "00", "TX-WRONG-AMOUNT");
        var mismatchResult = ingress.receive(mismatch);

        assertThat(invalidResult.message()).isEqualTo("INVALID_SIGNATURE");
        assertThat(mismatchResult.message()).isEqualTo("AMOUNT_CURRENCY_MISMATCH");
        assertThat(ingressReceipts.findAll()).noneMatch(receipt ->
                receipt.getMerchantReference().equals(fixture.merchantReference()));
        assertThat(bookings.findByBookingCode(fixture.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(paymentReceipts.findByBookingId(fixture.bookingId())).isEmpty();
        verify(operations, never()).commitBookingHolds(any());
    }

    @Test
    void signedStatus01IsRecordedAsNonterminalAndDoesNotConfirmUntilLaterSuccess() {
        Fixture fixture = pendingBooking();
        configureTicketProjection(fixture.sessionReference());
        var pending = ingress.receive(signedCallback(fixture.merchantReference(), "5000000", "00", "01", "TX-LATER"));
        var successful = ingress.receive(signedCallback(fixture.merchantReference(), "5000000", "00", "00", "TX-LATER"));

        assertThat(pending.message()).isEqualTo("OK");
        assertThat(successful.message()).isEqualTo("OK");
        assertThat(ingressReceipts.findAll()).anySatisfy(receipt -> {
            if (receipt.getMerchantReference().equals(fixture.merchantReference())
                    && "01".equals(receipt.getTransactionStatus())) {
                assertThat(receipt.isTerminalOutcome()).isFalse();
            }
        });
        assertThat(bookings.findByBookingCode(fixture.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentReceipts.findByBookingId(fixture.bookingId())).hasSize(1);
        verify(operations, times(1)).commitBookingHolds(any());
    }

    @Test
    void browserReturnWithEvenValidProviderParametersCannotConfirmBooking() throws Exception {
        Fixture fixture = pendingBooking();
        clearInvocations(operations);
        Map<String, String> callback = signedCallback(fixture.merchantReference(), "5000000", "00", "00", "TX-REDIRECT");

        mvc.perform(get("/payment/vnpay/return").params(toMvcParams(callback)))
                .andExpect(status().isUnauthorized());

        assertThat(bookings.findByBookingCode(fixture.bookingCode()).orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(paymentReceipts.findByBookingId(fixture.bookingId())).isEmpty();
        assertThat(ingressReceipts.findAll()).noneMatch(receipt ->
                receipt.getMerchantReference().equals(fixture.merchantReference()));
        verifyNoInteractions(operations);
    }

    private Fixture pendingBooking() {
        String bookingCode = "BK-DIT-" + UUID.randomUUID().toString().substring(0, 8);
        UUID session = UUID.randomUUID();
        Instant starts = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant ends = starts.plusSeconds(3600);
        when(operations.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(
                true, "PUBLISHED", 1L, starts, ends, 10));
        when(operations.reserveBookingCapacity(any())).thenReturn(CapacityHoldOutcome.accepted(
                List.of(UUID.randomUUID()), Instant.now().plusSeconds(600)));

        WorkshopBooking booking = lifecycle.createPendingBookingReservation(bookingCode, 1L, "Deposit IT package",
                "buyer@example.test", "0900000000", 77L, 1, List.of(session), 100_000L, 100_000L,
                "RESERVE-" + bookingCode, "CORR-" + bookingCode);
        BookingInvoice invoice = invoices.findByBookingId(booking.getId()).orElseThrow();
        String merchantReference = "DEP-" + bookingCode + "-" + UUID.randomUUID();
        BookingPaymentAttempt attempt = attempts.save(BookingPaymentAttempt.builder().booking(booking)
                .invoice(invoice).attemptReference("ATT-" + UUID.randomUUID())
                .merchantReference(merchantReference).attemptAmount(invoice.getDepositAmount())
                .status(PaymentAttemptStatus.PENDING).expiresAt(booking.getHoldExpiresAt()).build());
        owners.register(merchantReference, bookingCode, PaymentOwnerKind.DEPOSIT, invoice.getDepositAmount(), "VND");
        return new Fixture(booking.getId(), bookingCode, attempt.getMerchantReference(), session);
    }

    private void configureTicketProjection(UUID session) {
        Instant starts = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant ends = starts.plusSeconds(3600);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        when(operations.commitBookingHolds(any())).thenReturn(CapacityCommitOutcome.accepted());
        when(operations.getBookingTicketProjection(any())).thenReturn(new DeliverySafeTicketSet(List.of(
                new DeliverySafeTicket(UUID.randomUUID(), "QR-DEPOSIT-IT-" + suffix, "TICKET-DEPOSIT-IT-" + suffix, List.of(
                        new SessionEntitlementProjection(session, 1L, starts, ends))))));
    }

    private Map<String, String> signedCallback(String merchant, String minorUnits, String responseCode,
                                               String transactionStatus, String transactionNo) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", merchant);
        params.put("vnp_Amount", minorUnits);
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TransactionNo", transactionNo);
        params.put("vnp_OrderInfo", "Workshop deposit");
        params.put("vnp_TmnCode", "TEST1234");
        params.put("vnp_SecureHash", checksum.sign(params));
        return params;
    }

    private org.springframework.util.MultiValueMap<String, String> toMvcParams(Map<String, String> params) {
        org.springframework.util.LinkedMultiValueMap<String, String> result = new org.springframework.util.LinkedMultiValueMap<>();
        params.forEach(result::add);
        return result;
    }

    private record Fixture(Long bookingId, String bookingCode, String merchantReference, UUID sessionReference) { }
}
