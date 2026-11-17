package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailVerifiedPayment;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetailPaymentEvidenceServiceTest {
    @Mock GroupedCheckoutRepository checkouts;
    @Mock RetailVariantHoldRepository holdRepository;
    @Mock RetailVariantHoldLifecycleService holds;
    @Mock RetailPaymentReconciliationRepository reconciliation;
    @Mock CatalogueSalesFacade catalogue;
    @Mock AvailabilityGuardPort guard;
    @Mock LoyaltyFacade loyalty;
    @InjectMocks RetailPaymentEvidenceService service;

    @Test void expiredCheckoutRecordsEvidenceWithoutSettlingStockOrBenefits() {
        var checkout = mock(GroupedCheckout.class);
        when(checkouts.lockById("checkout-1")).thenReturn(Optional.of(checkout));
        when(checkout.getPayableAmount()).thenReturn(new BigDecimal("300000"));
        when(checkout.getCurrency()).thenReturn("VND");
        when(checkout.getPaymentState()).thenReturn(PaymentState.EXPIRED);
        when(holdRepository.findByGroupedCheckoutId("checkout-1")).thenReturn(List.of());
        service.applyVerifiedPayment(new RetailVerifiedPayment("checkout-1", "merchant-1", "tx-1", 300000, "VND"));
        var saved = ArgumentCaptor.forClass(RetailPaymentReconciliation.class);
        verify(reconciliation).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo("LATE_OR_INVALID_STATE");
        assertThat(saved.getValue().getProviderTransaction()).isEqualTo("tx-1");
        verifyNoInteractions(holds, catalogue, guard, loyalty);
    }

    @Test void alreadyPaidWithSameEvidenceDoesNotSettleAgain() {
        var checkout = mock(GroupedCheckout.class);
        when(checkouts.lockById("checkout-1")).thenReturn(Optional.of(checkout));
        when(checkout.getPayableAmount()).thenReturn(new BigDecimal("300000"));
        when(checkout.getCurrency()).thenReturn("VND");
        when(checkout.getPaymentState()).thenReturn(PaymentState.PAID);
        when(checkout.getPaymentProofRef()).thenReturn("VNPAY-tx-1");
        service.applyVerifiedPayment(new RetailVerifiedPayment("checkout-1", "merchant-1", "tx-1", 300000, "VND"));
        verifyNoInteractions(holdRepository, reconciliation, holds, catalogue, guard, loyalty);
    }
}
