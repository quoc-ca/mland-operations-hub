package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.AbsorbedUnderchargeDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.AbsorbedUnderchargeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementUnprocessableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** T135 and T156: transport rules of the absorbed-undercharge routes. */
class AbsorbedUnderchargeControllerTest {

    private static final String KEY = "UNDERCHARGE-KEY-12345";
    private static final String RECORD_URL = "/api/v1/workshop-tickets/T-1/folio/absorbed-undercharges";
    private final AbsorbedUnderchargeService service = mock(AbsorbedUnderchargeService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final CurrentAccountActor manager =
            new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new AbsorbedUnderchargeController(service))
                .setControllerAdvice(new WorkshopSettlementProblemHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .setValidator(validator)
                .build();
    }

    private static AbsorbedUnderchargeDto dto() {
        return new AbsorbedUnderchargeDto("UNDERCHARGE-1", "T-1", "G-1", 7L, "120000", "Wrong price", "ACTIVE", "10",
                Instant.parse("2026-11-16T05:00:00Z"), null, null, null);
    }

    @Test
    void recordsWithTheContractBodyAndAnswers201() throws Exception {
        when(service.record(eq("T-1"), eq(KEY), any(), eq(120_000L), eq("Wrong price"), eq(manager))).thenReturn(dto());

        mvc.perform(post(RECORD_URL).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountVnd\":\"120000\",\"reason\":\"Wrong price\"}")
                        .principal(new UsernamePasswordAuthenticationToken(manager, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.underchargeReference").value("UNDERCHARGE-1"))
                .andExpect(jsonPath("$.amountVnd").value("120000"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void invalidBodiesAnswer400() throws Exception {
        for (String body : List.of(
                "{\"amountVnd\":\"0\",\"reason\":\"r\"}",
                "{\"amountVnd\":\"-5\",\"reason\":\"r\"}",
                "{\"amountVnd\":\"12.5\",\"reason\":\"r\"}",
                "{\"amountVnd\":\"abc\",\"reason\":\"r\"}",
                "{\"reason\":\"r\"}",
                "{\"amountVnd\":\"1000\"}",
                "{\"amountVnd\":\"1000\",\"reason\":\" \"}",
                "{not json")) {
            mvc.perform(post(RECORD_URL).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                            .content(body).principal(new UsernamePasswordAuthenticationToken(manager, null)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void theCapAnswers422WithItsNamedCode() throws Exception {
        when(service.record(any(), any(), any(), anyLong(), any(), any()))
                .thenThrow(new SettlementUnprocessableException("UNDERCHARGE_EXCEEDS_FOLIO_TOTAL"));

        mvc.perform(post(RECORD_URL).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountVnd\":\"999999999\",\"reason\":\"r\"}")
                        .principal(new UsernamePasswordAuthenticationToken(manager, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNDERCHARGE_EXCEEDS_FOLIO_TOTAL"));
    }

    @Test
    void voidAnswers200AndTheListNeedsItsThreeParameters() throws Exception {
        when(service.voidRecord(eq("T-1"), eq("UNDERCHARGE-1"), eq(KEY), any(), eq("Typed twice"), eq(manager)))
                .thenReturn(dto());

        mvc.perform(post(RECORD_URL + "/UNDERCHARGE-1/voids").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Typed twice\"}")
                        .principal(new UsernamePasswordAuthenticationToken(manager, null)))
                .andExpect(status().isOk());

        var principal = new UsernamePasswordAuthenticationToken(manager, null);
        mvc.perform(get("/api/v1/workshop-absorbed-undercharges").param("locationId", "7").param("from", "2026-11-01")
                .principal(principal)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/workshop-absorbed-undercharges").param("from", "2026-11-01").param("to", "2026-11-30")
                .principal(principal)).andExpect(status().isBadRequest());
    }
}
