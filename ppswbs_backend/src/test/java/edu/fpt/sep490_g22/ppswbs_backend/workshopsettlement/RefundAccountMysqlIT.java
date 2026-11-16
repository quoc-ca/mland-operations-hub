package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundAccountLinkRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundLinkSubmissionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundAccountLinkStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementNotificationHandoff;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountCipher;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountKeyReplacementService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * 003.3 T146, T137, T159, T169, T170 on MySQL: the refund account link and its lifecycle, the key replacement, the
 * token scrub of the stored email and the per-ticket refund cap under concurrency. Real services and repositories on
 * the Flyway-built schema; only the other modules' facades and the payment ports are mocked.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant",
        "workshop.operations.visible-location-ids="})
class RefundAccountMysqlIT {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String ACCOUNT = "12345678901";

    @MockitoBean PaymentGatewayCheckoutPort checkout;
    @MockitoBean VnpayMerchantOperationsPort merchantOperations;
    @MockitoBean WorkshopOperationsFacade operationsFacade;
    @MockitoBean LoyaltyFacade loyalty;
    @MockitoBean WorkshopBookingFacade bookingFacade;
    @MockitoBean edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade bookingNotifications;

    @Autowired SettlementFinalBillRepository bills;
    @Autowired SettlementRefundRequestRepository refunds;
    @Autowired SettlementRefundAccountLinkRepository links;
    @Autowired SettlementRefundLinkSubmissionRepository submissions;
    @Autowired SettlementNotificationHandoffRepository handoffs;
    @Autowired BankRefundService bankRefunds;
    @Autowired RefundAccountKeyReplacementService keyReplacement;

    @BeforeAll
    static void refundAccountKey() {
        ReflectionTestUtils.invokeMethod(RefundAccountCipher.class, "useKeyForTests",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeEach
    void recipient() {
        doReturn("payer@example.com").when(bookingFacade).getSettlementNotificationRecipient(any());
    }

    private String ticketWithCollected(long collected) {
        String ticket = "T-RA-" + SEQ.incrementAndGet();
        bills.save(SettlementFinalBill.builder().billReference("BILL-" + ticket).bookingReference(ticket)
                .groupReference("G-RA").postPromotionAmountVnd(collected).acceptedDepositAmountVnd(collected)
                .remainingAmountVnd(0L).currencyCode("VND").lifecycleStatus("OPEN").build());
        return ticket;
    }

    private List<SettlementNotificationHandoff> emails(String requestReference) {
        return handoffs.findAll().stream().filter(h -> requestReference.equals(h.getSettlementReference())
                && "REFUND_ACCOUNT_REQUEST".equals(h.getNotificationKind())).toList();
    }

    private static String key() {
        return "it-link-key-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("T146/T159/T169: a link is spent once and replays; a return voids the approval and the number, and every token leaves the stored emails")
    void returnPathAndTokenScrub() {
        String ticket = ticketWithCollected(500_000);
        var opened = bankRefunds.open(ticket, "G-RA", RefundReason.EXCESS_OR_DUPLICATE_MONEY, "src-" + ticket, 200_000, "CORR");
        String reference = opened.request().getRequestReference();
        assertThat(emails(reference)).singleElement().satisfies(mail ->
                assertThat(mail.getActionLink()).endsWith(opened.rawAccountLinkToken()));

        String idem = key();
        bankRefunds.submitAccountByLink(opened.rawAccountLinkToken(), idem, "BIDV", ACCOUNT, "NGUYEN VAN A", true, "CORR");
        // A lost-response retry replays: no second write, and the stored fingerprint holds no account value.
        bankRefunds.submitAccountByLink(opened.rawAccountLinkToken(), idem, "BIDV", ACCOUNT, "NGUYEN VAN A", true, "CORR");
        assertThat(submissions.findAll().stream().filter(s -> s.getIdempotencyKey().equals(idem)))
                .singleElement().satisfies(row -> assertThat(row.getRequestFingerprint()).doesNotContain(ACCOUNT));
        assertThat(emails(reference)).singleElement().satisfies(mail -> assertThat(mail.getActionLink()).isNull());
        assertThat(links.findAll().stream().filter(l -> l.getRequestId().equals(opened.request().getId())))
                .singleElement().satisfies(l -> assertThat(l.getLifecycleStatus()).isEqualTo(RefundAccountLinkStatus.USED));

        bankRefunds.approve(reference, 22L, "ok", "CORR");
        var returned = bankRefunds.returnToPendingAccount(reference, 22L, "Ngan hang tra lai", "CORR");

        var row = refunds.findByRequestReference(reference).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
        assertThat(row.getRecipientAccountEncrypted()).isNull();
        assertThat(row.getRecipientAccountMasked()).isEqualTo("****8901");
        assertThat(row.getApprovedByAccountId()).isNull();
        assertThat(row.getAmountVnd()).isEqualTo(200_000);
        assertThat(emails(reference)).hasSize(2).filteredOn(m -> m.getActionLink() != null).singleElement()
                .satisfies(mail -> assertThat(mail.getActionLink()).endsWith(returned.rawAccountLinkToken()));

        // A late return after REFUNDED is refused and changes nothing.
        bankRefunds.submitAccountByLink(returned.rawAccountLinkToken(), key(), "BIDV", ACCOUNT, "NGUYEN VAN A", true, "CORR");
        bankRefunds.approve(reference, 22L, "ok", "CORR");
        bankRefunds.recordTransfer(reference, 33L, "FT-" + SEQ.incrementAndGet(), null, "CORR");
        bankRefunds.confirmRefunded(reference, 22L, "CORR");
        try {
            bankRefunds.returnToPendingAccount(reference, 22L, "late", "CORR");
        } catch (WorkshopSettlementConflictException expected) {
            assertThat(expected.getMessage()).isEqualTo("REFUND_NOT_RETURNABLE");
        }
        assertThat(refunds.findByRequestReference(reference).orElseThrow().getStatus()).isEqualTo(RefundRequestStatus.REFUNDED);
    }

    @Test
    @DisplayName("T137: the key replacement returns every request holding a cipher; a rerun and the expiry sweep change nothing else")
    void keyReplacementAndExpirySweep() {
        String ticket = ticketWithCollected(500_000);
        var opened = bankRefunds.open(ticket, "G-RA", RefundReason.EXCESS_OR_DUPLICATE_MONEY, "src-" + ticket, 100_000, "CORR");
        String reference = opened.request().getRequestReference();
        bankRefunds.submitAccountByLink(opened.rawAccountLinkToken(), key(), "BIDV", ACCOUNT, "NGUYEN VAN A", true, "CORR");
        bankRefunds.approve(reference, 22L, "ok", "CORR");

        var result = keyReplacement.replace(1L, "Khoa bi lo", "CORR");

        assertThat(result.returnedToPendingAccount()).isGreaterThanOrEqualTo(1);
        assertThat(result.failed()).isZero();
        var row = refunds.findByRequestReference(reference).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(RefundRequestStatus.PENDING_ACCOUNT);
        assertThat(row.getRecipientAccountEncrypted()).isNull();
        assertThat(row.getRecipientAccountMasked()).isEqualTo("****8901");
        assertThat(row.getApprovedAt()).isNull();
        assertThat(links.findAll().stream().filter(l -> l.getRequestId().equals(row.getId()))
                .filter(l -> l.getLifecycleStatus() == RefundAccountLinkStatus.ACTIVE)).hasSize(1);
        assertThat(keyReplacement.replace(1L, "Khoa bi lo", "CORR").returnedToPendingAccount()).isZero();

        // The link expires unused: the sweep clears its token from the stored email.
        assertThat(emails(reference).stream().filter(m -> m.getActionLink() != null)).hasSize(1);
        bankRefunds.scrubExpiredLinkTokens(Instant.now().plus(8, ChronoUnit.DAYS));
        assertThat(emails(reference).stream().filter(m -> m.getActionLink() != null)).isEmpty();
    }

    @Test
    @DisplayName("T170: two requests opened together never pass what was collected; a remainder of 0 returns the existing request")
    void capHoldsUnderConcurrency() throws Exception {
        String ticket = ticketWithCollected(500_000);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of("a", "b").stream().map(suffix -> pool.submit((Callable<Boolean>) () -> {
                start.await();
                try {
                    bankRefunds.open(ticket, "G-RA", RefundReason.EXCESS_OR_DUPLICATE_MONEY, "src-" + suffix + "-" + ticket, 300_000, "CORR");
                    return true;
                } catch (WorkshopSettlementConflictException refused) {
                    return false;
                }
            })).toList();
            start.countDown();
            long accepted = 0;
            for (Future<Boolean> result : results) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(refunds.sumRefundedOrPending(ticket)).isEqualTo(300_000);

        var remainder = bankRefunds.openRemainder(ticket, "G-RA", RefundReason.MLAND_CANCELLATION, "case-" + ticket, "CORR");
        assertThat(remainder.request().getAmountVnd()).isEqualTo(200_000);
        var none = bankRefunds.openRemainder(ticket, "G-RA", RefundReason.OVERCHARGE_AFTER_FINALIZED, "ov-" + ticket, "CORR");
        assertThat(none.request().getRequestReference()).isEqualTo(remainder.request().getRequestReference());
        assertThat(refunds.sumRefundedOrPending(ticket)).isEqualTo(500_000);
    }
}
