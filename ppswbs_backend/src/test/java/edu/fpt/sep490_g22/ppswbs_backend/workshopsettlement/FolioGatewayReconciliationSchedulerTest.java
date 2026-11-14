package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentIngressOutcomeQueryPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VerifiedTerminalPaymentOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationResult;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayQueryDrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionMethod;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.FolioCollectionStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.ApplyVerifiedSettlementPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalPaymentService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioGatewayReconciliationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.3 T109: a pending VNPAY folio part is failed only on VNPay's own signed answer (IPN or QueryDR status 02), is
 * confirmed through the normal evidence path on a signed QueryDR success, and is left alone on anything else.
 */
class FolioGatewayReconciliationSchedulerTest {

    private static final String MERCHANT = "FOL0123456789ABCDEF0123456789ABCDEF";

    private SettlementFolioCollectionRepository collections;
    private PaymentIngressOutcomeQueryPort ingress;
    private VnpayMerchantOperationsPort merchant;
    private FolioCollectionService folioCollections;
    private FinalPaymentService finalPayments;
    private FolioGatewayReconciliationScheduler scheduler;
    private SettlementFolioCollection part;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        collections = mock(SettlementFolioCollectionRepository.class);
        ingress = mock(PaymentIngressOutcomeQueryPort.class);
        merchant = mock(VnpayMerchantOperationsPort.class);
        folioCollections = mock(FolioCollectionService.class);
        finalPayments = mock(FinalPaymentService.class);
        scheduler = new FolioGatewayReconciliationScheduler(collections, ingress, merchant, folioCollections,
                finalPayments, "203.0.113.5");
        expiresAt = Instant.now().minus(Duration.ofMinutes(7));
        part = SettlementFolioCollection.builder().collectionReference("COLL-1").billReference("BILL-1")
                .bookingReference("TK-1").method(FolioCollectionMethod.VNPAY).amountVnd(80_000L)
                .status(FolioCollectionStatus.PENDING_VERIFICATION).externalReference(MERCHANT)
                .paymentExpiresAt(expiresAt).providerCreateDate("20261114100000").build();
        when(collections.findTop100ByMethodAndStatusOrderByIdAsc(FolioCollectionMethod.VNPAY,
                FolioCollectionStatus.PENDING_VERIFICATION)).thenReturn(List.of(part));
        when(ingress.findVerifiedTerminalOutcome(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void aSignedIpnWithStatus02FailsThePartWithoutAskingVnpay() {
        when(ingress.findVerifiedTerminalOutcome(MERCHANT)).thenReturn(Optional.of(
                new VerifiedTerminalPaymentOutcome(MERCHANT, 80_000L, "VND", "24", "02", "IPN-7")));

        scheduler.reconcilePendingGatewayParts();

        verify(folioCollections).failGatewayCollectionFromProvider(MERCHANT, "VNPAY_IPN_TRANSACTION_STATUS_02", "IPN-7");
        verifyNoInteractions(merchant, finalPayments);
    }

    @Test
    void aSuccessfulIpnIsLeftToTheIngressDispatch() {
        when(ingress.findVerifiedTerminalOutcome(MERCHANT)).thenReturn(Optional.of(
                new VerifiedTerminalPaymentOutcome(MERCHANT, 80_000L, "VND", "00", "00", "IPN-8")));

        scheduler.reconcilePendingGatewayParts();

        verifyNoInteractions(merchant, finalPayments);
        verify(folioCollections, never()).failGatewayCollectionFromProvider(any(), any(), any());
    }

    @Test
    void nothingIsAskedBeforeTheCheckoutExpires() {
        part.setPaymentExpiresAt(Instant.now().plus(Duration.ofMinutes(3)));

        scheduler.reconcilePendingGatewayParts();

        verifyNoInteractions(merchant, finalPayments);
        verify(folioCollections, never()).failGatewayCollectionFromProvider(any(), any(), any());
    }

    @Test
    void nothingIsAskedOnceTheQueryWindowIsOver() {
        part.setPaymentExpiresAt(Instant.now().minus(Duration.ofHours(25)));

        scheduler.reconcilePendingGatewayParts();

        verifyNoInteractions(merchant);
    }

    @Test
    void aSignedQueryDrWithStatus02FailsThePartOncePerFiveMinuteSlot() {
        when(merchant.queryTransaction(any())).thenReturn(result("00", "02", MERCHANT, 80_000L, true, null));

        scheduler.reconcilePendingGatewayParts();

        ArgumentCaptor<VnpayQueryDrCommand> query = ArgumentCaptor.forClass(VnpayQueryDrCommand.class);
        verify(merchant).queryTransaction(query.capture());
        assertThat(query.getValue().operationReference()).isEqualTo("FQDR-COLL-1-1");
        assertThat(query.getValue().merchantReference()).isEqualTo(MERCHANT);
        assertThat(query.getValue().originalTransactionDate()).isEqualTo("20261114100000");
        verify(folioCollections).failGatewayCollectionFromProvider(MERCHANT, "VNPAY_QUERYDR_TRANSACTION_STATUS_02",
                "FQDR-COLL-1-1");
        verifyNoInteractions(finalPayments);
    }

    @Test
    void aSignedQueryDrSuccessIsAppliedAsVerifiedEvidenceUnderTheIpnEvidenceReference() {
        when(merchant.queryTransaction(any())).thenReturn(result("00", "00", MERCHANT, 80_000L, true, "14420000"));
        when(finalPayments.apply(any())).thenReturn(SettlementPaymentEvidenceOutcome.accepted());

        scheduler.reconcilePendingGatewayParts();

        ArgumentCaptor<ApplyVerifiedSettlementPaymentEvidenceCommand> applied =
                ArgumentCaptor.forClass(ApplyVerifiedSettlementPaymentEvidenceCommand.class);
        verify(finalPayments).apply(applied.capture());
        assertThat(applied.getValue().verifiedEvidenceReference()).isEqualTo("VNPAY-14420000");
        assertThat(applied.getValue().operationReference()).isEqualTo("VNPAY-14420000");
        assertThat(applied.getValue().merchantReference()).isEqualTo(MERCHANT);
        assertThat(applied.getValue().paymentKind()).isEqualTo("FINAL_PAYMENT");
        assertThat(applied.getValue().amountVnd()).isEqualTo(80_000L);
        assertThat(applied.getValue().originalProviderCreateDate()).isEqualTo("20261114100000");
        verify(folioCollections, never()).failGatewayCollectionFromProvider(any(), any(), any());
    }

    @Test
    void unsignedPendingOrMismatchedAnswersChangeNothing() {
        when(merchant.queryTransaction(any()))
                .thenReturn(result("00", "02", MERCHANT, 80_000L, false, null))   // unsigned
                .thenReturn(result("00", "01", MERCHANT, 80_000L, true, null))    // still pending
                .thenReturn(result("00", "02", "FOL-OTHER", 80_000L, true, null)) // another payment
                .thenReturn(result("00", "02", MERCHANT, 79_000L, true, null))    // another amount
                .thenReturn(result("99", "02", MERCHANT, 80_000L, true, null));   // query not answered

        for (int i = 0; i < 5; i++) {
            scheduler.reconcilePendingGatewayParts();
        }

        verify(merchant, times(5)).queryTransaction(any());
        verifyNoInteractions(finalPayments);
        verify(folioCollections, never()).failGatewayCollectionFromProvider(any(), any(), any());
    }

    @Test
    void withoutTheMerchantApiAddressNoQueryIsSent() {
        scheduler = new FolioGatewayReconciliationScheduler(collections, ingress, merchant, folioCollections,
                finalPayments, "");

        scheduler.reconcilePendingGatewayParts();

        verifyNoInteractions(merchant);
    }

    private static VnpayMerchantOperationResult result(String responseCode, String transactionStatus,
                                                       String merchantReference, long amount, boolean signed,
                                                       String transactionNumber) {
        return new VnpayMerchantOperationResult("op", "QUERY", "RECONCILED", responseCode, "01", transactionStatus,
                transactionNumber, merchantReference, amount, signed);
    }
}
