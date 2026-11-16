package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementUnprocessableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.3 T165/T169/T150, FR-040: the public refund-account link route. The four outcomes of a link (unknown 404,
 * used, revoked or expired 410), the Idempotency-Key replay and conflict, and the entry confirmation. No answer
 * echoes an account number or a token.
 */
class RefundAccountLinkControllerTest {

    private static final String KEY = "idem-key-00000001";
    private static final String BODY = """
            {"bankCode":"BIDV","accountNumber":"12345678901","holderName":"A","accountDetailsConfirmed":true}""";

    private final BankRefundService bankRefunds = mock(BankRefundService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RefundAccountLinkController(bankRefunds))
                .setControllerAdvice(new WorkshopSettlementProblemHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter()).build();
    }

    @Test
    void anAccepted_submission_answers204WithNoBody() throws Exception {
        when(bankRefunds.submitAccountByLink(eq("tok-1"), eq(KEY), eq("BIDV"), eq("12345678901"), eq("A"), eq(true),
                anyString())).thenReturn(mock(SettlementRefundRequest.class));

        mvc.perform(post("/api/v1/refund-account-links/tok-1").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test
    void anUnknownTokenIs404WithoutEchoingTheTokenOrTheNumber() throws Exception {
        when(bankRefunds.submitAccountByLink(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyString())).thenThrow(new WorkshopSettlementNotFoundException());

        mvc.perform(post("/api/v1/refund-account-links/secret-token-xyz").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-token-xyz"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12345678901"))));
    }

    @Test
    void aUsedRevokedOrExpiredLinkIs410AndSoIsARequestThatIsNoLongerAwaitingAnAccount() throws Exception {
        for (String code : new String[]{"REFUND_ACCOUNT_LINK_NOT_USABLE", "REFUND_NOT_AWAITING_ACCOUNT"}) {
            reset(bankRefunds);
            when(bankRefunds.submitAccountByLink(anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyBoolean(), anyString())).thenThrow(new WorkshopSettlementConflictException(code));

            mvc.perform(post("/api/v1/refund-account-links/tok-2").header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value("REFUND_ACCOUNT_LINK_NOT_USABLE"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("tok-2"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12345678901"))));
        }
    }

    @Test
    void changedContentUnderTheSameKeyIs409IdempotencyConflict() throws Exception {
        when(bankRefunds.submitAccountByLink(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyString())).thenThrow(new WorkshopSettlementConflictException("IDEMPOTENCY_CONFLICT"));

        mvc.perform(post("/api/v1/refund-account-links/tok-3").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void aMissingOrShortKeyIs422AndNothingReachesTheService() throws Exception {
        mvc.perform(post("/api/v1/refund-account-links/tok-4").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(post("/api/v1/refund-account-links/tok-4").header("Idempotency-Key", "short")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(bankRefunds);
    }

    @Test
    void anAbsentOrFalseConfirmationIs422AccountConfirmationRequired() throws Exception {
        when(bankRefunds.submitAccountByLink(anyString(), anyString(), anyString(), anyString(), anyString(),
                eq(false), anyString())).thenThrow(new SettlementUnprocessableException("ACCOUNT_CONFIRMATION_REQUIRED"));

        for (String body : new String[]{
                "{\"bankCode\":\"BIDV\",\"accountNumber\":\"12345678901\",\"holderName\":\"A\"}",
                "{\"bankCode\":\"BIDV\",\"accountNumber\":\"12345678901\",\"holderName\":\"A\",\"accountDetailsConfirmed\":false}"}) {
            mvc.perform(post("/api/v1/refund-account-links/tok-5").header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_CONFIRMATION_REQUIRED"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12345678901"))));
        }
    }
}
