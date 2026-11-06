package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailVariantHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VnpayRetailPaymentAdapterTest {
    @Test void registersGroupedCheckoutAndCreatesUrlWithoutClaimingPaymentSuccess() {
        var gateway = mock(PaymentGatewayCheckoutPort.class);
        var owners = mock(PaymentOwnerRegistrationPort.class);
        var holds = mock(RetailVariantHoldRepository.class);
        var hold = mock(RetailVariantHold.class);
        when(hold.getStatus()).thenReturn(RetailVariantHoldStatus.HELD);
        when(hold.getExpiresAt()).thenReturn(Instant.now().plusSeconds(900));
        when(holds.findByGroupedCheckoutId("checkout-1")).thenReturn(List.of(hold));
        when(gateway.createRetailPaymentUrl(anyString(), anyString(), anyLong(), any(), any(), any()))
                .thenReturn("https://sandbox.example/pay");
        var adapter = new VnpayRetailPaymentAdapter(gateway, owners, holds);
        ReflectionTestUtils.setField(adapter, "vnpayEnabled", true);
        var result = adapter.initiate("checkout-1", new BigDecimal("300000"));
        assertThat(result.locallyVerified()).isFalse();
        assertThat(result.paymentUrl()).isEqualTo("https://sandbox.example/pay");
        verify(owners).registerOwner(eq("RETAIL-checkout-1"), eq("checkout-1"), eq("RETAIL"), eq(300000L), eq("VND"), anyString());
    }

    @Test void disabledProviderCannotRegisterPayment() {
        var owners = mock(PaymentOwnerRegistrationPort.class);
        var adapter = new VnpayRetailPaymentAdapter(mock(PaymentGatewayCheckoutPort.class), owners, mock(RetailVariantHoldRepository.class));
        assertThatThrownBy(() -> adapter.initiate("checkout-1", new BigDecimal("300000")))
                .isInstanceOf(RetailIntegrationUnavailableException.class);
        verifyNoInteractions(owners);
    }
}
