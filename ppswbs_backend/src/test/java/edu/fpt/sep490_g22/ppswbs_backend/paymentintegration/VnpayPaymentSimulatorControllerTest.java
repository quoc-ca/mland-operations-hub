package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config.VnpayProperties;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.controller.VnpayPaymentSimulatorController;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerRegistration;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.PaymentIngressService;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.VnpayChecksumService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentSimulatorControllerTest {
    @Mock PaymentOwnerRegistry registry;
    @Mock PaymentIngressService ingress;
    private VnpayChecksumService checksum;
    private VnpayPaymentSimulatorController controller;

    private final PaymentOwnerRegistration owner = PaymentOwnerRegistration.builder()
            .merchantReference("FINAL-BK-1-1").bookingReference("BK-1").paymentKind(PaymentOwnerKind.FINAL_PAYMENT)
            .amountVnd(75_000L).currencyCode("VND").providerCreateDate("20261030181359").build();

    @BeforeEach void setUp() {
        VnpayProperties properties = new VnpayProperties();
        properties.setTmnCode("TMN1");
        properties.setHashSecret("secret-for-test");
        checksum = new VnpayChecksumService(properties);
        controller = new VnpayPaymentSimulatorController(registry, ingress, checksum, properties);
    }

    @Test void confirmSendsASignedSuccessIpnForTheRegisteredAmount() {
        when(registry.resolve("FINAL-BK-1-1")).thenReturn(Optional.of(owner));
        when(ingress.receive(anyMap())).thenReturn(new PaymentIngressService.Outcome("00", "OK"));

        String page = controller.confirm("FINAL-BK-1-1");

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(ingress).receive(sent.capture());
        Map<String, String> params = sent.getValue();
        assertThat(params).containsEntry("vnp_TxnRef", "FINAL-BK-1-1").containsEntry("vnp_Amount", "7500000")
                .containsEntry("vnp_ResponseCode", "00").containsEntry("vnp_TransactionStatus", "00");
        assertThat(params.get("vnp_TransactionNo")).isNotBlank().hasSizeLessThanOrEqualTo(15);
        assertThat(checksum.valid(params, params.get("vnp_SecureHash"))).isTrue();
        assertThat(page).contains("Thanh toán thành công");
    }

    @Test void confirmReportsARejectionInsteadOfClaimingSuccess() {
        when(registry.resolve("FINAL-BK-1-1")).thenReturn(Optional.of(owner));
        when(ingress.receive(anyMap())).thenReturn(new PaymentIngressService.Outcome("04", "AMOUNT_CURRENCY_MISMATCH"));

        String page = controller.confirm("FINAL-BK-1-1");

        assertThat(page).contains("chưa được ghi nhận").contains("AMOUNT_CURRENCY_MISMATCH")
                .doesNotContain("Thanh toán thành công");
    }

    @Test void anAlreadyConfirmedPaymentIsStillShownAsPaid() {
        when(registry.resolve("FINAL-BK-1-1")).thenReturn(Optional.of(owner));
        when(ingress.receive(anyMap())).thenReturn(new PaymentIngressService.Outcome("02", "Already received"));

        assertThat(controller.confirm("FINAL-BK-1-1")).contains("Thanh toán thành công");
    }

    @Test void unknownReferenceNeverReachesTheIngress() {
        when(registry.resolve("NOPE")).thenReturn(Optional.empty());

        assertThat(controller.confirm("NOPE")).contains("Không tìm thấy giao dịch");
        assertThat(controller.page("NOPE")).contains("Không tìm thấy giao dịch");
        verify(ingress, never()).receive(anyMap());
    }

    @Test void pageShowsTheAmountAndAPostFormToTheSamePath() {
        when(registry.resolve("FINAL-BK-1-1")).thenReturn(Optional.of(owner));

        String page = controller.page("FINAL-BK-1-1");

        assertThat(page).contains("75,000 VND").contains("method=\"post\"")
                .contains("/api/v1/integrations/payments/simulator/FINAL-BK-1-1");
    }
}
