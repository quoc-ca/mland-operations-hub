package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashCloseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashCloseResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.ExpectedCashResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionApiService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** T131 (C2): the cash-close routes speak the contract's names; the frontend body is accepted as is. */
class FolioCollectionControllerTest {

    private static final String KEY = "CLOSE-KEY-1234567890";
    private static final String CLOSES = "/api/v1/workshop-cash-closes";

    private final FolioCollectionApiService api = mock(FolioCollectionApiService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final CurrentAccountActor staff =
            new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(7L));
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new FolioCollectionController(api))
                .setControllerAdvice(new WorkshopSettlementProblemHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()
                        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .setValidator(validator)
                .build();
    }

    private static CashCloseResponse response() {
        return new CashCloseResponse("CASHCLOSE-1", 7L, LocalDate.of(2026, 11, 16), 100_000L, 90_000L, -10_000L,
                "Thieu", "PENDING_MANAGER_CONFIRMATION");
    }

    @Test
    void postsTheExactFrontendBodyEndToEnd() throws Exception {
        when(api.closeCashDay(any(), eq(KEY), any(), eq(staff))).thenReturn(response());

        mvc.perform(post(CLOSES).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":7,\"workingDate\":\"2026-11-16\",\"countedAmount\":\"90000\","
                                + "\"differenceReason\":\"Thieu\"}")
                        .principal(new UsernamePasswordAuthenticationToken(staff, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closeReference").value("CASHCLOSE-1"))
                .andExpect(jsonPath("$.locationId").value(7))
                .andExpect(jsonPath("$.workingDate").value("2026-11-16"))
                .andExpect(jsonPath("$.countedCashVnd").value(90000))
                .andExpect(jsonPath("$.branchId").doesNotExist())
                .andExpect(jsonPath("$.businessDay").doesNotExist());

        ArgumentCaptor<CashCloseRequest> body = ArgumentCaptor.forClass(CashCloseRequest.class);
        verify(api).closeCashDay(body.capture(), eq(KEY), any(), eq(staff));
        assertThat(body.getValue().locationId()).isEqualTo(7L);
        assertThat(body.getValue().workingDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(body.getValue().countedAmountVnd()).isEqualTo(90_000L);
        assertThat(body.getValue().differenceReason()).isEqualTo("Thieu");
    }

    @Test
    void malformedBodiesAnswer400() throws Exception {
        for (String body : List.of(
                "{\"branchId\":7,\"businessDay\":\"2026-11-16\",\"countedCashVnd\":90000}",
                "{\"workingDate\":\"2026-11-16\",\"countedAmount\":\"90000\"}",
                "{\"locationId\":7,\"countedAmount\":\"90000\"}",
                "{\"locationId\":7,\"workingDate\":\"2026-11-16\"}",
                "{\"locationId\":7,\"workingDate\":\"2026-11-16\",\"countedAmount\":\"90.5\"}",
                "{\"locationId\":7,\"workingDate\":\"2026-11-16\",\"countedAmount\":\"-1\"}",
                "{\"locationId\":7,\"workingDate\":\"2026-11-16\",\"countedAmount\":\"abc\"}",
                "{\"locationId\":7,\"workingDate\":\"not-a-date\",\"countedAmount\":\"1\"}",
                "{not json")) {
            mvc.perform(post(CLOSES).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                            .content(body).principal(new UsernamePasswordAuthenticationToken(staff, null)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(api);
    }

    @Test
    void aDifferenceWithoutAReasonAnswers422() throws Exception {
        when(api.closeCashDay(any(), any(), any(), any()))
                .thenThrow(new WorkshopSettlementValidationException("CASH_DIFFERENCE_REASON_REQUIRED"));

        mvc.perform(post(CLOSES).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":7,\"workingDate\":\"2026-11-16\",\"countedAmount\":\"1\"}")
                        .principal(new UsernamePasswordAuthenticationToken(staff, null)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void expectedCashReadsLocationIdAndWorkingDate() throws Exception {
        when(api.expectedCash(eq(7L), eq(LocalDate.of(2026, 11, 16)), eq(staff)))
                .thenReturn(new ExpectedCashResponse(7L, LocalDate.of(2026, 11, 16), 150_000L,
                        java.util.List.of(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashEntry(
                                "COLLECTION", "T-1", 150_000L, java.time.Instant.parse("2026-11-16T03:00:00Z")))));

        mvc.perform(get(CLOSES + "/expected-cash").param("locationId", "7").param("workingDate", "2026-11-16")
                        .principal(new UsernamePasswordAuthenticationToken(staff, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(7))
                .andExpect(jsonPath("$.workingDate").value("2026-11-16"))
                .andExpect(jsonPath("$.expectedCashVnd").value(150000))
                .andExpect(jsonPath("$.entries[0].type").value("COLLECTION"))
                .andExpect(jsonPath("$.entries[0].ticketCode").value("T-1"))
                .andExpect(jsonPath("$.entries[0].amountVnd").value(150000))
                .andExpect(jsonPath("$.entries[0].recordedAt").exists());

        mvc.perform(get(CLOSES + "/expected-cash").param("branchId", "7")
                        .principal(new UsernamePasswordAuthenticationToken(staff, null)))
                .andExpect(status().isBadRequest());
    }
}
