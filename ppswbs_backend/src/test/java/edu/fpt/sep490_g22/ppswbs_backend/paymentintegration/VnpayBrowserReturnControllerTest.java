package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import static org.assertj.core.api.Assertions.assertThat;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.controller.VnpayBrowserReturnController;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class VnpayBrowserReturnControllerTest {
    private final VnpayBrowserReturnController controller = new VnpayBrowserReturnController("http://localhost:5173");

    @Test
    void retailReturnGoesToOrdersWithoutExposingSignature() {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("vnp_OrderInfo", "Retail checkout checkout-123");
        params.add("vnp_ResponseCode", "00");
        params.add("vnp_SecureHash", "secret-signature");
        var location = controller.redirectToDisplayPage(params).getHeaders().getLocation().toString();
        assertThat(location).startsWith("http://localhost:5173/member/orders?")
                .contains("vnp_ResponseCode=00").doesNotContain("vnp_SecureHash", "secret-signature");
    }

    @Test
    void workshopAndUnknownReturnsKeepTheirExistingDestinations() {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("vnp_OrderInfo", "Workshop deposit BK-123");
        assertThat(controller.redirectToDisplayPage(params).getHeaders().getLocation().getPath())
                .isEqualTo("/workshop-booking/BK-123/payment-return");
        params.set("vnp_OrderInfo", "unknown");
        assertThat(controller.redirectToDisplayPage(params).getHeaders().getLocation().getPath())
                .isEqualTo("/workshop-booking/lookup");
    }
}
