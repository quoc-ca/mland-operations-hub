package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.Method;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.ReconciliationCollectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.ReconciliationDtos.State;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.ReconciliationCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.ReconciliationSourceUnavailableException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** T133 (FR-056): the transport rules of the reconciliation route. */
class ReconciliationCollectionControllerTest {

    private static final String URL = "/api/v1/workshop-reconciliation-collections";
    private final ReconciliationCollectionService service = mock(ReconciliationCollectionService.class);
    private final CurrentAccountActor manager =
            new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private final CurrentAccountActor staff =
            new CurrentAccountActor(20L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(7L));
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new ReconciliationCollectionController(service))
                .setControllerAdvice(new WorkshopSettlementProblemHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    void managerReadsTheListWithTheContractFieldNames() throws Exception {
        when(service.list(eq(7L), eq(LocalDate.of(2026, 11, 16)), eq(Method.BANK_TRANSFER), eq(State.CONFIRMED),
                eq(manager))).thenReturn(List.of(new ReconciliationCollectionDto("C-1",
                Instant.parse("2026-11-16T05:00:00Z"), "T-1", List.of("T-1"), "G-1", "1200000", null, null,
                Method.BANK_TRANSFER, State.CONFIRMED, "BANK-1", "10", List.of("7"), null, null)));

        mvc.perform(get(URL).param("locationId", "7").param("workingDate", "2026-11-16")
                        .param("method", "BANK_TRANSFER").param("state", "CONFIRMED")
                        .principal(new UsernamePasswordAuthenticationToken(manager, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].collectionId").value("C-1"))
                .andExpect(jsonPath("$[0].state").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$[0].amount").value("1200000"))
                .andExpect(jsonPath("$[0].locationIds[0]").value("7"));
    }

    @Test
    void staffAnswers403() throws Exception {
        when(service.list(any(), any(), any(), any(), eq(staff)))
                .thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));

        mvc.perform(get(URL).param("locationId", "7").param("workingDate", "2026-11-16")
                        .principal(new UsernamePasswordAuthenticationToken(staff, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOrMalformedParametersAnswer400() throws Exception {
        var principal = new UsernamePasswordAuthenticationToken(manager, null);
        mvc.perform(get(URL).param("workingDate", "2026-11-16").principal(principal)).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("locationId", "7").principal(principal)).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("locationId", "7").param("workingDate", "16-11-2026").principal(principal))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("locationId", "7").param("workingDate", "2026-11-16").param("method", "CASH")
                .principal(principal)).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("locationId", "7").param("workingDate", "2026-11-16").param("state", "FAILED")
                .principal(principal)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void anUnavailable003_1SourceAnswers503WithTheContractCode() throws Exception {
        when(service.list(any(), any(), any(), any(), any()))
                .thenThrow(new ReconciliationSourceUnavailableException(new IllegalStateException("down")));

        mvc.perform(get(URL).param("locationId", "7").param("workingDate", "2026-11-16")
                        .principal(new UsernamePasswordAuthenticationToken(manager, null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_SOURCE_UNAVAILABLE"));
    }
}
