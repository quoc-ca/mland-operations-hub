package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetailQueryReconciliationServiceTest {
    @Mock PaymentOwnerRegistrationPort owners;
    @Mock PaymentIngressOutcomeQueryPort outcomes;
    @Mock VnpayMerchantOperationsPort merchant;
    @Mock GroupedCheckoutRepository checkouts;
    @Mock RetailVariantHoldRepository holds;
    @Mock RetailPaymentReconciliationRepository reconciliation;
    @Mock RetailConfirmedUnpaidExpiryService expiry;
    @Mock RetailPaymentEvidenceFacade evidence;
    @Mock PlatformTransactionManager manager;
    RetailQueryReconciliationService service;
    final Instant deadline = Instant.parse("2026-11-04T10:00:00Z");

    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        service = new RetailQueryReconciliationService(owners, outcomes, merchant, checkouts, holds,
                reconciliation, expiry, evidence, manager, "127.0.0.1", true);
        when(owners.lockRetailPayment("RETAIL-checkout-1")).thenReturn(Optional.of(
                new PaymentOwnerRegistrationPort.LockedRetailPayment("checkout-1", 150000L, "VND", "20261104170000")));
        var checkout = mock(GroupedCheckout.class);
        when(checkout.getMemberAccountId()).thenReturn(1L);
        when(checkout.getPaymentState()).thenReturn(PaymentState.PAYMENT_INITIATED);
        when(checkouts.lockById("checkout-1")).thenReturn(Optional.of(checkout));
        var hold = mock(RetailVariantHold.class);
        when(hold.getExpiresAt()).thenReturn(deadline);
        when(holds.findByGroupedCheckoutId("checkout-1")).thenReturn(List.of(hold));
    }

    @Test void confirmedFailureUsesDurableQueryReferenceForExpiry() {
        when(merchant.queryTransaction(any())).thenReturn(result("02", true));
        service.reconcile(1L, "checkout-1", deadline.plusSeconds(301));
        verify(merchant).queryTransaction(argThat(command -> command.operationReference().equals("RQ-checkout-1-1")));
        verify(expiry).expire(1L, "checkout-1", "RETAIL-checkout-1", "RQ-checkout-1-1");
        verifyNoInteractions(evidence);
    }

    @Test void unverifiedQueryNeverExpiresOrMarksPaid() {
        when(merchant.queryTransaction(any())).thenReturn(result("02", false));
        service.reconcile(1L, "checkout-1", deadline.plusSeconds(10));
        verify(expiry, never()).expire(anyLong(), anyString(), anyString(), anyString());
        verifyNoInteractions(evidence, reconciliation);
    }

    @Test void unknownAfterSixtyMinutesIsRecordedForManager() {
        service.reconcile(1L, "checkout-1", deadline.plusSeconds(3600));
        var row = ArgumentCaptor.forClass(RetailPaymentReconciliation.class);
        verify(reconciliation).saveAndFlush(row.capture());
        assertThat(row.getValue().getReason()).isEqualTo("QUERY_UNRESOLVED");
        assertThat(row.getValue().getProviderTransaction()).isNull();
        verifyNoInteractions(merchant, evidence);
    }

    @Test void verifiedLateSuccessIsPassedToRetailEvidenceHandler() {
        when(merchant.queryTransaction(any())).thenReturn(result("00", true));
        service.reconcile(1L, "checkout-1", deadline.plusSeconds(10));
        verify(evidence).applyVerifiedPayment(argThat(payment -> payment.checkoutId().equals("checkout-1")
                && payment.providerTransaction().equals("tx-1") && payment.amountVnd() == 150000L));
    }

    private VnpayMerchantOperationResult result(String status, boolean verified) {
        return new VnpayMerchantOperationResult("op", "QUERYDR", "RECONCILED", "00", "01", status,
                "tx-1", "RETAIL-checkout-1", 150000L, verified);
    }
}
