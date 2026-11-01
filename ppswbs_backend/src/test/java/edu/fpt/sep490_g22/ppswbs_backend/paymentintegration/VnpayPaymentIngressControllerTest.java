package edu.fpt.sep490_g22.ppswbs_backend.paymentintegration;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.controller.VnpayPaymentIngressController;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.service.PaymentIngressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentIngressControllerTest {
    @Mock PaymentIngressService ingress;
    private MockMvc mvc;

    @BeforeEach void setUp() { mvc = MockMvcBuilders.standaloneSetup(new VnpayPaymentIngressController(ingress)).setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper())).build(); }

    @Test void mapsProviderQueryToSafeAcknowledgement() throws Exception {
        when(ingress.receive(anyMap())).thenReturn(new PaymentIngressService.Outcome("00", "OK"));
        mvc.perform(get("/api/v1/integrations/payments/vnpay/events")
                        .queryParam("vnp_TxnRef", "DEP-BK-1-1")
                        .queryParam("vnp_Amount", "5000000")
                        .queryParam("vnp_ResponseCode", "00")
                        .queryParam("vnp_SecureHash", "redacted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("OK"));
        verify(ingress).receive(anyMap());
    }
}
