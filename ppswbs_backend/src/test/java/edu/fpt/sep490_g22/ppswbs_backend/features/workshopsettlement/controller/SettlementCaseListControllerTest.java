package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.SettlementCaseListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

class SettlementCaseListControllerTest {
    private final SettlementCaseListService service = mock(SettlementCaseListService.class);
    private final Authentication authentication = mock(Authentication.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new SettlementCaseListController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        when(authentication.getPrincipal()).thenReturn(
                new CurrentAccountActor(7L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of()));
    }

    @Test
    void managerListsCasesWithExactFilters() throws Exception {
        when(service.list(any(), eq("CUSTOMER_CANCELLATION"), eq("OPEN"), eq(25)))
                .thenReturn(List.of(new SettlementCaseProjectionDto(
                        "CASE-1", "CUSTOMER_CANCELLATION", "OPEN", "BK-1", "CORR-1")));

        mvc.perform(get("/api/v1/workshop-settlement-cases")
                        .param("caseType", "CUSTOMER_CANCELLATION")
                        .param("status", "OPEN")
                        .param("limit", "25")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseReference").value("CASE-1"))
                .andExpect(jsonPath("$[0].bookingCode").value("BK-1"));

        verify(service).list(any(), eq("CUSTOMER_CANCELLATION"), eq("OPEN"), eq(25));
    }

    @Test
    void listWithoutFiltersPassesNullFilters() throws Exception {
        when(service.list(any(), isNull(), isNull(), isNull())).thenReturn(List.of());

        mvc.perform(get("/api/v1/workshop-settlement-cases")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(service).list(any(), isNull(), isNull(), isNull());
    }
}
