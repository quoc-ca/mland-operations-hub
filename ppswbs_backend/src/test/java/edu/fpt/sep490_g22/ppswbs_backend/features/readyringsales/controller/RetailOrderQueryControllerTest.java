package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailMemberActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailOrderQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailUnauthorizedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.RetailOrderHistoryView;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetailOrderQueryControllerTest {
    private final RetailActorResolver actors = mock(RetailActorResolver.class);
    private final RetailOrderQueryService orders = mock(RetailOrderQueryService.class);
    private MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RetailOrderQueryController(actors, orders))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
            .setControllerAdvice(new RetailExceptionHandler()).build();
    }

    @Test void anonymousRequestDoesNotReadOrders() throws Exception {
        when(actors.requireActiveMember(any(HttpServletRequest.class))).thenThrow(new RetailUnauthorizedException("anonymous"));
        mvc.perform(get("/api/v1/members/me/ready-ring-orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void foreignOrderReturnsGenericNotFound() throws Exception {
        when(actors.requireActiveMember(any(HttpServletRequest.class))).thenReturn(new RetailMemberActor(7L));
        when(orders.find(7L, "foreign")).thenThrow(new RetailNotFoundException("not found"));
        mvc.perform(get("/api/v1/members/me/ready-ring-orders/foreign"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test void ownerCanReadSafeSavedOrderFacts() throws Exception {
        when(actors.requireActiveMember(any(HttpServletRequest.class))).thenReturn(new RetailMemberActor(7L));
        var item = new RetailOrderHistoryView.Item(100L, "Current name", "SKU-100", 5, 2, "100", "200");
        var order = new RetailOrderHistoryView("order", 5L, "PICKUP", "PAID", null,
            "200", "0", "200", Instant.parse("2026-10-30T00:00:00Z"), List.of(item));
        when(orders.find(7L, "order")).thenReturn(order);
        mvc.perform(get("/api/v1/members/me/ready-ring-orders/order"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productName").value("Current name"))
            .andExpect(jsonPath("$.items[0].unitPrice").value("100"));
    }
}
