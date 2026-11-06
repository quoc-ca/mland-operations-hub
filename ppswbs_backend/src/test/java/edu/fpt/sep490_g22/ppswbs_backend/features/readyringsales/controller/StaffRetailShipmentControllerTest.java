package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentTransitionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailShipmentView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailAccessDeniedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailUnauthorizedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.StaffRetailShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffRetailShipmentControllerTest {

    private final StaffRetailShipmentService service = mock(StaffRetailShipmentService.class);
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StaffRetailShipmentController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .setControllerAdvice(new RetailExceptionHandler())
            .build();
    }

    private static RequestPostProcessor asActor(CurrentAccountActor actor) {
        return request -> {
            request.setAttribute("currentAccountActor", actor);
            return request;
        };
    }

    private static CurrentAccountActor staff(long id) {
        return new CurrentAccountActor(id, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(5L));
    }

    @Test
    void listRejectsUnauthenticated() throws Exception {
        when(service.list(any(), any())).thenThrow(new RetailUnauthorizedException("auth"));
        mvc.perform(get("/api/v1/staff/retail-shipments"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listRejectsNonStaffRole() throws Exception {
        when(service.list(any(), any())).thenThrow(new RetailAccessDeniedException("forbidden"));
        mvc.perform(get("/api/v1/staff/retail-shipments").with(asActor(staff(1L))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listReturnsBoundedPage() throws Exception {
        var summary = new StaffRetailShipmentSummary("ship-1", "order-1", "order-1", 5L, "S1234567890",
            RetailShipmentStatus.PREPARING, 0, "Nguyen A", "Ha Noi", "Ba Dinh", "Lieu Giai",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(service.list(any(), any())).thenReturn(new StaffRetailShipmentSummary.Page(List.of(summary), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/staff/retail-shipments").with(asActor(staff(1L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("ship-1"))
            .andExpect(jsonPath("$.items[0].status").value("PREPARING"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detailNotFoundReturns404() throws Exception {
        when(service.detail(any(), eq("missing"))).thenThrow(new RetailNotFoundException("not found"));
        mvc.perform(get("/api/v1/staff/retail-shipments/missing").with(asActor(staff(1L))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void detailReturnsView() throws Exception {
        var view = new StaffRetailShipmentView("ship-1", "order-1", "order-1", 5L, "GHTK", "S1234567890",
            RetailShipmentStatus.PREPARING, 0, "Nguyen A", "0900", "12 Addr", "Ha Noi", "Ba Dinh", "Lieu Giai",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), List.of());
        when(service.detail(any(), eq("ship-1"))).thenReturn(view);
        mvc.perform(get("/api/v1/staff/retail-shipments/ship-1").with(asActor(staff(1L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mockTrackingNumber").value("S1234567890"))
            .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void transitionAdvancesShipment() throws Exception {
        when(service.transition(any(), eq("ship-1"), eq(RetailShipmentStatus.HANDED_TO_CARRIER), eq(0), anyString()))
            .thenReturn(new StaffRetailShipmentTransitionResult("ship-1", RetailShipmentStatus.HANDED_TO_CARRIER, 1,
                Instant.parse("2026-01-01T00:00:00Z")));
        String body = mapper.writeValueAsString(new StaffRetailShipmentSummary.TransitionRequest(
            RetailShipmentStatus.HANDED_TO_CARRIER, 0, "packed"));
        mvc.perform(post("/api/v1/staff/retail-shipments/ship-1/transitions").with(asActor(staff(1L)))
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("HANDED_TO_CARRIER"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void transitionMissingNextStatusIsRejectedByValidation() throws Exception {
        String body = "{\"expectedVersion\":0}";
        mvc.perform(post("/api/v1/staff/retail-shipments/ship-1/transitions").with(asActor(staff(1L)))
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void transitionConflictOnStaleVersion() throws Exception {
        when(service.transition(any(), eq("ship-1"), eq(RetailShipmentStatus.HANDED_TO_CARRIER), eq(5), anyString()))
            .thenThrow(new RetailConflictException("stale"));
        String body = mapper.writeValueAsString(new StaffRetailShipmentSummary.TransitionRequest(
            RetailShipmentStatus.HANDED_TO_CARRIER, 5, "packed"));
        mvc.perform(post("/api/v1/staff/retail-shipments/ship-1/transitions").with(asActor(staff(1L)))
                .contentType("application/json").content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RETAIL_CONFLICT"));
    }
}
