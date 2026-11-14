package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCorrectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketFolioApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TicketFolioControllerTest {

    private final TicketFolioApiService folioApi = mock(TicketFolioApiService.class);
    private final FolioCorrectionService correctionService = mock(FolioCorrectionService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    private final CurrentAccountActor manager = new CurrentAccountActor(10L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new TicketFolioController(folioApi, correctionService))
                .setControllerAdvice(new WorkshopSettlementProblemHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .build();
    }

    @Test
    void readFolioReturnsFolioDto() throws Exception {
        TicketFolioDto dto = new TicketFolioDto(
                "TK-1", "OPEN", "100000", "20000", "0", "80000",
                List.of(), List.of()
        );
        when(folioApi.readFolio(eq("TK-1"), any())).thenReturn(dto);

        mvc.perform(get("/api/v1/workshop-tickets/TK-1/folio")
                        .principal(new UsernamePasswordAuthenticationToken(manager, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value("TK-1"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.balanceDue").value("80000"));

        verify(folioApi).readFolio(eq("TK-1"), eq(manager));
    }

    @Test
    void guestLookupReturnsNoStoreCacheHeader() throws Exception {
        TicketFolioDto dto = new TicketFolioDto(
                "TK-2", "OPEN", "100000", "20000", "0", "80000",
                List.of(), List.of()
        );
        when(folioApi.lookupFolio(eq("TK-2"), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/workshop-tickets/TK-2/folio-lookups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactEmail":"buyer@example.test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.ticketCode").value("TK-2"));

        verify(folioApi).lookupFolio(eq("TK-2"), any(GuestFolioLookupRequest.class));
    }

    @Test
    void finalizeFolioDelegatesToService() throws Exception {
        TicketFolioDto dto = new TicketFolioDto(
                "TK-3", "FINALIZED", "100000", "100000", "0", "0",
                List.of(), List.of()
        );
        when(folioApi.finalizeFolio(eq("TK-3"), eq("key-1234567890123456"), any(), eq(manager)))
                .thenReturn(dto);

        mvc.perform(post("/api/v1/workshop-tickets/TK-3/folio/finalizations")
                        .header("Idempotency-Key", "key-1234567890123456")
                        .principal(new UsernamePasswordAuthenticationToken(manager, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));
    }
}
